/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package org.perfcake.validation;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.perfcake.PerfCakeException;
import org.perfcake.message.Message;
import org.perfcake.message.ReceivedMessage;

/**
 * A manager of validator that should validate message responses.
 * 
 * @author Martin Večeřa <marvenec@gmail.com>
 * @author Lucie Fabriková <lucie.fabrikova@gmail.com>
 */
public class ValidatorManager {

   /**
    * A map of validators: validator id => validator instance
    */
   private final TreeMap<String, MessageValidator> validators = new TreeMap<>();

   /**
    * An internal thread that takes one response after another and validates them.
    */
   private Thread validationThread = null;

   /**
    * Indicates whether the validation is finished. Starts with true as there is no validation running at the beginning.
    */
   private boolean finished = true;

   /**
    * Were all messages validated properly so far?
    */
   private boolean allMessagesValid = true;

   /**
    * Is validation enabled?
    */
   private boolean enabled = false;

   /**
    * At the end, when there is nothing else to do, we can go through the remaining responses faster. Otherwise, the validation
    * thread has some sleep for it not to influence measurement.
    */
   private boolean fastForward = false;

   /**
    * A logger.
    */
   private final Logger log = Logger.getLogger(ValidatorManager.class);

   /**
    * A queue with the message responses.
    */
   private Queue<ReceivedMessage> resultMessages;

   /**
    * Creates a new validator menager. The message responses are store in a file queue in a temporary file.
    * 
    * @throws PerfCakeException
    *            When it was not possible to initialize the message store.
    */
   public ValidatorManager() throws PerfCakeException {
      try {
         final File tmpFile = File.createTempFile("perfcake", "queue");
         tmpFile.deleteOnExit();
         setQueueFile(tmpFile);
      } catch (final IOException e) {
         throw new PerfCakeException("Cannot create a file queue for messages to be validated: ", e);
      }
   }

   /**
    * Sets a different location of the file queue for storing message responses.
    * 
    * @param queueFile
    *           The new location of the file queue.
    * @throws PerfCakeException
    *            When it was not possible to initialize the file queue or there is a running validation.
    */
   public void setQueueFile(final File queueFile) throws PerfCakeException {
      if (isFinished()) {
         resultMessages = new FileQueue<ReceivedMessage>(queueFile);
      } else {
         throw new PerfCakeException("It is not possible to change the file queue while there is a running validation.");
      }
   }

   /**
    * Adds a new message validator.
    * 
    * @param validatorId
    *           A string id of the new validator.
    * @param messageValidator
    *           A validator instance.
    */
   public void addValidator(final String validatorId, final MessageValidator messageValidator) {
      validators.put(validatorId, messageValidator);
   }

   /**
    * Gets the validator with the given id.
    * 
    * @param validatorId
    *           A string id of the validator.
    * @return The validator instance or null if there is no such validator with the given id.
    */
   public MessageValidator getValidator(final String validatorId) {
      return validators.get(validatorId);
   }

   /**
    * Starts the validation process. This mainly means starting a new validator thread.
    */
   public void startValidation() {
      if (validationThread == null || !validationThread.isAlive()) {
         validationThread = new Thread(new ValidationThread());
         validationThread.setDaemon(true); // we do not want to block JVM
         validationThread.start();
      }
   }

   /**
    * Wait for the validation to be finished. The call is blocked until the validator thread finishes execution or an exception
    * is thrown. Internally, this joins the validator thread to the current thread.
    * 
    * @throws InterruptedException
    *            If the validator thread was interrupted.
    */
   public void waitForValidation() throws InterruptedException {
      if (validationThread != null) {
         fastForward = true;
         validationThread.join();
      }
   }

   /**
    * Interrupts the validator thread immediately. There might be remaining unfinished validations.
    */
   public void terminateNow() {
      if (validationThread != null) {
         validationThread.interrupt();
      }
   }

   /**
    * Internal class representing the validator thread. The thread validates one message with all registered validators and then
    * sleeps for 500ms. This is needed for the validation not to influence measurement. After a call to {@link #waitForValidation()} the
    * sleeps are skipped.
    */
   private class ValidationThread implements Runnable {

      @Override
      public void run() {
         boolean isMessageValid = false;
         ReceivedMessage receivedMessage = null;
         finished = false;
         allMessagesValid = true;
         fastForward = false;

         if (validators.isEmpty()) {
            log.warn("No validators set in scenario.");
            return;
         }

         try {
            while (!validationThread.isInterrupted() && (receivedMessage = resultMessages.poll()) != null) {
               for (final MessageValidator validator : receivedMessage.getSentMessage().getValidators()) {
                  isMessageValid = validator.isValid(new Message(receivedMessage.getPayload()));
                  if (log.isTraceEnabled()) {
                     log.trace(String.format("Message response %s validated with %s returns %s.", receivedMessage.getPayload().toString(), validator.toString(), String.valueOf(isMessageValid)));
                  }

                  allMessagesValid &= isMessageValid;
               }
               if (!fastForward) {
                  Thread.sleep(500); // we do not want to block senders
               }
            }
         } catch (final InterruptedException ex) {
            // never mind, we have been asked to terminate
         }

         if (log.isInfoEnabled()) {
            log.info("The validator thread finished with result " + (allMessagesValid ? "all messages are valid." : "there were validation errors."));
         }

         finished = true;
      }
   }

   /**
    * Adds a new message response to be validated.
    * 
    * @param receivedMessage
    *           The message response to be validated.
    */
   public void addToResultMessages(final ReceivedMessage receivedMessage) {
      resultMessages.add(receivedMessage);
   }

   /**
    * Gets the number of messages that needs to be validated.
    * 
    * @return The current size of the file queue with messages waiting for validation.
    */
   public int getSize() {
      return resultMessages.size();
   }

   /**
    * Is validation facility enabled?
    * 
    * @return True if validation is enabled.
    */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * Enables/disables validation. This only takes effect before the validation is started.
    * 
    * @param enabled
    */
   public void setEnabled(final boolean enabled) {
      assert enabled == true || finished == true : "Validation cannot be disabled while the validation is in progress."; // we know this is about to disable the validation since it is running already

      this.enabled = enabled;
   }

   /**
    * Determines whether the validation process finished already.
    * 
    * @return True if the validation finished or was not started yet.
    */
   public boolean isFinished() {
      return finished;
   }

}
