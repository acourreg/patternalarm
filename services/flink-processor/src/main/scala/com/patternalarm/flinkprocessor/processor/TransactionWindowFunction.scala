package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model.{TimedWindowAggregate, TransactionEvent}
import org.apache.flink.streaming.api.functions.windowing.WindowFunction
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector

import java.lang
import java.time.Instant
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

/**
 * Window function that receives ALL transactions at once
 * Simpler than AggregateFunction for this use case
 */
class TransactionWindowFunction
    extends WindowFunction[TransactionEvent, TimedWindowAggregate, String, TimeWindow] {

  override def apply(
    actorId: String,
    window: TimeWindow,
    events: lang.Iterable[TransactionEvent],
    out: Collector[TimedWindowAggregate]
  ): Unit = {

    val eventList = events.asScala.toList

    if (eventList.nonEmpty) {
      val aggregate = TimedWindowAggregate(
        actorId = actorId,
        domain = eventList.head.domain,
        transactionCount = eventList.size,
        totalAmount = eventList.map(_.amount).sum,
        windowStart = Instant.ofEpochMilli(window.getStart),
        windowEnd = Instant.ofEpochMilli(window.getEnd),
        transactions = eventList
      )

      out.collect(aggregate)
    }
  }
}
