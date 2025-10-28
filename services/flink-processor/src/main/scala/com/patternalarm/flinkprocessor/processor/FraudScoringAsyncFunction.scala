package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{AsyncFunction, ResultFuture}
import sttp.client3._

import scala.collection.JavaConverters.asJavaCollectionConverter
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class FraudScoringAsyncFunction(fastapiUrl: String)
    extends AsyncFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)] {

  @transient private var backend: SttpBackend[Future, Any] = _
  @transient private var ec: ExecutionContext = _

  def open(parameters: Configuration): Unit = {
    println(s"🔌 Initializing FraudScoringAsyncFunction...")
    println(s"🔌 FastAPI URL: $fastapiUrl")
    backend = HttpClientFutureBackend()
    ec = scala.concurrent.ExecutionContext.global
    println(s"✅ HTTP client backend initialized")
  }

  def close(): Unit = {
    println("🔌 Closing HTTP client backend...")
    if (backend != null) {
      backend.close()
      println("✅ HTTP client closed")
    }
  }

  override def asyncInvoke(
    aggregate: TimedWindowAggregate,
    resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
  ): Unit = {

    println(s"📤 Preparing ML prediction request for actor=${aggregate.actorId}")
    
    val request = PredictRequest.fromAggregate(aggregate)
    val requestJson = JsonUtils.toJson(request)
    
    println(s"📤 Request JSON size: ${requestJson.length} chars")
    println(s"📤 Sending POST to: $fastapiUrl/predict")

    val responseFuture: Future[Response[String]] =
      basicRequest
        .post(uri"$fastapiUrl/predict")
        .contentType("application/json")
        .body(requestJson)
        .response(asStringAlways)
        .send(backend)

    responseFuture.onComplete {
      case Success(response) =>
        if (response.code.isSuccess) {
          println(s"✅ Received successful response (${response.code}) for actor=${aggregate.actorId}")
          try {
            val predictResponse = JsonUtils.fromJson[PredictResponse](response.body)
            println(s"✅ Parsed ML response: fraud_score=${predictResponse.fraudScore}, model=${predictResponse.modelVersion}")
            resultFuture.complete(Iterable((aggregate, predictResponse)).asJavaCollection)
          } catch {
            case e: Exception =>
              System.err.println(s"❌ ERROR: Failed to parse ML response for actor=${aggregate.actorId}")
              System.err.println(s"❌ Response body: ${response.body.take(500)}")
              System.err.println(s"❌ Parse error: ${e.getMessage}")
              e.printStackTrace()
              resultFuture.completeExceptionally(
                new RuntimeException(s"Failed to parse response: ${e.getMessage}", e)
              )
          }
        } else {
          System.err.println(s"❌ ERROR: FastAPI returned error ${response.code} for actor=${aggregate.actorId}")
          System.err.println(s"❌ Response body: ${response.body}")
          resultFuture.completeExceptionally(
            new RuntimeException(s"FastAPI error ${response.code}: ${response.body}")
          )
        }

      case Failure(exception) =>
        System.err.println(s"❌ ERROR: HTTP call failed for actor=${aggregate.actorId}")
        System.err.println(s"❌ Target URL: $fastapiUrl/predict")
        System.err.println(s"❌ Exception type: ${exception.getClass.getName}")
        System.err.println(s"❌ Exception message: ${exception.getMessage}")
        exception.printStackTrace()
        resultFuture.completeExceptionally(
          new RuntimeException(s"HTTP call failed to $fastapiUrl: ${exception.getMessage}", exception)
        )
    }(ec)
  }

  override def timeout(
    input: TimedWindowAggregate,
    resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
  ): Unit = {
    System.err.println(s"⏱️  TIMEOUT: ML prediction request timed out for actor=${input.actorId}")
    System.err.println(s"⏱️  Target URL: $fastapiUrl/predict")
    resultFuture.completeExceptionally(
      new RuntimeException(s"Timeout for actor ${input.actorId} calling $fastapiUrl")
    )
  }
}
