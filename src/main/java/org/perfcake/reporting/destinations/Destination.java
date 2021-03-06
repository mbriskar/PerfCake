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
package org.perfcake.reporting.destinations;

import java.io.Closeable;

import org.perfcake.reporting.Measurement;
import org.perfcake.reporting.ReportingException;

/**
 * Destination represents a channel to which performance measurement results can be reported.
 * Destinations are registered with {@link org.perfcake.reporting.reporters.Reporter Reporters} and are completely controlled by them. The only responsibility of a destination is to open
 * a reporting channel, report measurements, and close the reporting channel.
 * It is the role of {@link org.perfcake.reporting.Measurement} to provide all the information
 * to be reported (including value types, names, units and custom labels).
 * 
 * @author Martin Večera <marvenec@gmail.com>
 * 
 */
public interface Destination extends Closeable {

   /**
    * Opens the destination for reporting.
    */
   public void open();

   /**
    * Closes the destination. No other value should be reported after that.
    */
   @Override
   public void close();

   /**
    * Report a new {@link org.perfcake.reporting.Measurement} to the destination.
    * 
    * @param m
    *           A measurement to be reported
    * @throws ReportingException
    *            When an error occured during reporting the measurement like no space left on device. The root cause should be encapsulated.
    */
   public void report(Measurement m) throws ReportingException;
}
