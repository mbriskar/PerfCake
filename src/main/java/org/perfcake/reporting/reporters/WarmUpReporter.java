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
package org.perfcake.reporting.reporters;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.perfcake.PerfCakeConst;
import org.perfcake.common.PeriodType;
import org.perfcake.reporting.Measurement;
import org.perfcake.reporting.MeasurementUnit;
import org.perfcake.reporting.ReportingException;
import org.perfcake.reporting.destinations.Destination;
import org.perfcake.reporting.reporters.accumulators.Accumulator;
import org.perfcake.reporting.reporters.accumulators.SlidingWindowAvgAccumulator;

/**
 * <p>
 * The reporter is able to determine when the tested system is warmed up. The warming is enabled/disabled by the presence of the {@link WarmUpReporter} in the scenario. The minimal iteration count and
 * the warm-up period duration can be tweaked by the respective properties ({@link #minimalWarmUpCount} with the default value of 10,000 and {@link #minimalWarmUpDuration} with the default value of
 * 15,000 ms).
 * </p>
 * <p>
 * The system is considered warmed up when all of the following conditions are satisfied: The iteration length is not changing much over the time, the minimal iteration count has been executed and the
 * minimal duration from the very start has exceeded.
 * </p>
 * 
 * @author Pavel Macík <pavel.macik@gmail.com>
 * 
 */
public class WarmUpReporter extends AbstractReporter {

   /**
    * The reporter's logger.
    */
   private static final Logger log = Logger.getLogger(WarmUpReporter.class);

   /**
    * Minimal warm-up period duration in milliseconds.
    */
   private long minimalWarmUpDuration = 15000; // default 15s

   /**
    * Minimal iteration count executed during the warm-up period.
    */
   private long minimalWarmUpCount = 10000; // by JIT

   /**
    * The relative difference threshold to determine whether the throughput is not changing much.
    */
   private double relativeThreshold = 0.002d; // 0.2%

   /**
    * The absolute difference threshold to determine whether the throughput is not changing much.
    */
   private double absoluteThreshold = 0.2d; // 0.2

   /**
    * The flag indicating whether the tested system is considered warmed up.
    */
   private boolean warmed = false;

   /**
    * The index number of the checking period in which the current run is.
    * 
    * @see #checkingPeriod
    */
   private long checkingPeriodIndex = 0;

   /**
    * The period in milliseconds in which the checking if the tested system is warmed up.
    */
   private final long checkingPeriod = 1000;

   @Override
   public void start() {
      runInfo.addTag(PerfCakeConst.WARM_UP_TAG);
      if (log.isInfoEnabled()) {
         log.info("Warming the tested system up (for at least " + minimalWarmUpDuration + " ms and " + minimalWarmUpCount + " iterations) ...");
      }
      super.start();
   }

   @Override
   public void publishResult(final PeriodType periodType, final Destination d) throws ReportingException {
      throw new ReportingException("No destination is allowed on " + getClass().getSimpleName());
   }

   @SuppressWarnings("rawtypes")
   @Override
   protected Accumulator getAccumulator(final String key, final Class clazz) {
      return new SlidingWindowAvgAccumulator(16);
   }

   @Override
   protected void doReset() {
      // nothing to do
   }

   @Override
   protected synchronized void doReport(final MeasurementUnit mu) throws ReportingException {
      if (!warmed) {
         if (runInfo.getRunTime() / checkingPeriod > checkingPeriodIndex) {
            checkingPeriodIndex++;
            // The throughput unit is number of iterations per second
            final double currentThoughput = 1000.0 * runInfo.getIteration() / runInfo.getRunTime();
            final Double lastThroughput = (Double) getAccumulatedResult(Measurement.DEFAULT_RESULT);
            if (lastThroughput != null) {
               final double relDelta = Math.abs(currentThoughput / lastThroughput - 1.0);
               final double absDelta = Math.abs(currentThoughput - lastThroughput);
               if (log.isTraceEnabled()) {
                  log.trace("checkingPeriodIndex=" + checkingPeriodIndex + ", currentThroughput=" + currentThoughput + ", lastThroughput=" + lastThroughput + ", absDelta=" + absDelta + ", relDelta=" + relDelta);
               }
               if ((runInfo.getRunTime() > minimalWarmUpDuration) && (runInfo.getIteration() > minimalWarmUpCount) && (absDelta < absoluteThreshold || relDelta < relativeThreshold)) {
                  if (log.isInfoEnabled()) {
                     log.info("The tested system is warmed up.");
                  }
                  runInfo.reset();
                  runInfo.removeTag(PerfCakeConst.WARM_UP_TAG);
                  warmed = true;
               }
            }
            final Map<String, Object> result = new HashMap<>();
            result.put(Measurement.DEFAULT_RESULT, Double.valueOf(currentThoughput));
            accumulateResults(result);
         }
      }
   }

   @Override
   protected boolean checkStart() {
      return true;
   }

   /**
    * Used to read the value of minimal warm-up period duration.
    * 
    * @return The minimal warm-up period duration.
    */
   public long getMinimalWarmUpDuration() {
      return minimalWarmUpDuration;
   }

   /**
    * Sets the value of minimal warm-up period duration.
    * 
    * @param minimalWarmUpDuration
    *           The minimal warm-up period duration to set.
    */
   public void setMinimalWarmUpDuration(final long minimalWarmUpDuration) {
      this.minimalWarmUpDuration = minimalWarmUpDuration;
   }

   /**
    * Used to read the value of minimal warm-up iteration count.
    * 
    * @return The value of minimal warm-up iteration count.
    */
   public long getMinimalWarmUpCount() {
      return minimalWarmUpCount;
   }

   /**
    * Sets the value of minimal warm-up iteration count.
    * 
    * @param minimalWarmUpCount
    *           The value of minimal warm-up iteration count to set.
    */
   public void setMinimalWarmUpCount(final long minimalWarmUpCount) {
      this.minimalWarmUpCount = minimalWarmUpCount;
   }

   /**
    * Used to read the value of relativeThreshold.
    * 
    * @return The value of relativeThreshold.
    */
   public double getRelativeThreshold() {
      return relativeThreshold;
   }

   /**
    * Sets the value of relativeThreshold.
    * 
    * @param relativeThreshold
    *           The value of relativeThreshold to set.
    */
   public void setRelativeThreshold(final double relativeThreshold) {
      this.relativeThreshold = relativeThreshold;
   }

   /**
    * Used to read the value of absoluteThreshold.
    * 
    * @return The value of absoluteThreshold.
    */
   public double getAbsoluteThreshold() {
      return absoluteThreshold;
   }

   /**
    * Sets the value of absoluteThreshold.
    * 
    * @param absoluteThreshold
    *           The value of absoluteThreshold to set.
    */
   public void setAbsoluteThreshold(final double absoluteThreshold) {
      this.absoluteThreshold = absoluteThreshold;
   }

}
