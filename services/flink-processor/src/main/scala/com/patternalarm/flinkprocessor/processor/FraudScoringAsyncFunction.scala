package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FraudScoringAsyncFunction(fastapiUrl: String)
  extends RichAsyncFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)] {

  @transient private var backend: SttpBackend[Future, Any] = _
  @transient private var ec: ExecutionContext = _

  override def open(parameters: Configuration): Unit = {
    backend = AsyncHttpClientFutureBackend()
    ec = scala.concurrent.ExecutionContext.global
  }

  override def close(): Unit = {
    if (backend != null) backend.close()
  }

  override def asyncInvoke(
                            aggregate: TimedWindowAggregate,
                            resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                          ): Unit = {

    val timestamp = System.currentTimeMillis()

    val request = PredictRequest.fromAggregate(aggregate)
    val requestJson = JsonUtils.toJson(request)

    // 📤 LOG REQUEST JSON (minified)
    println(s"📤 REQ actor=${aggregate.actorId} json=$requestJson")

    val responseFuture = basicRequest
      .post(uri"$fastapiUrl/predict")
      .contentType("application/json")
      .body(requestJson)
      .response(asStringAlways)
      .send(backend)

    import scala.concurrent.ExecutionContext.Implicits.global

    responseFuture.onComplete {
      case Success(response) =>
        val elapsed = System.currentTimeMillis() - timestamp
        if (response.code.isSuccess) {
          try {
            val predictResponse = JsonUtils.fromJson[PredictResponse](response.body)
            // 🎯 LOG RESPONSE
            println(s"🎯 RES actor=${aggregate.actorId} score=${predictResponse.fraudScore} model=${predictResponse.modelVersion} elapsed=${elapsed}ms")
            resultFuture.complete(Iterable((aggregate, predictResponse)).asJavaCollection)
          } catch {
            case e: Exception =>
              System.err.println(s"❌ PARSE actor=${aggregate.actorId} err=${e.getMessage} body=${response.body.take(200)}")
              resultFuture.completeExceptionally(new RuntimeException(s"Parse failed: ${e.getMessage}", e))
          }
        } else {
          System.err.println(s"❌ API actor=${aggregate.actorId} code=${response.code} body=${response.body.take(200)}")
          resultFuture.completeExceptionally(new RuntimeException(s"API error ${response.code}: ${response.body}"))
        }

      case Failure(exception) =>
        val elapsed = System.currentTimeMillis() - timestamp
        System.err.println(s"❌ HTTP actor=${aggregate.actorId} err=${exception.getMessage} elapsed=${elapsed}ms")
        resultFuture.completeExceptionally(new RuntimeException(s"HTTP failed: ${exception.getMessage}", exception))
    }
  }

  override def timeout(
                        input: TimedWindowAggregate,
                        resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                      ): Unit = {
    System.err.println(s"⏱️ TIMEOUT actor=${input.actorId}")
    resultFuture.completeExceptionally(new RuntimeException(s"Timeout for actor ${input.actorId}"))
  }
}