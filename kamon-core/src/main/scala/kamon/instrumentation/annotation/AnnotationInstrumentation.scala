package kamon.instrumentation.annotation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.annotation.{Gauge, Counter, Trace}
import kamon.metric.UserMetrics
import kamon.trace.{MetricsOnlyContext, TraceRecorder}
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.{After, AfterReturning, Around, Aspect}

@Aspect
class AnnotationInstrumentation {
  implicit lazy val system = AnnotationBla.system

  @Around("execution(@kamon.annotation.Trace * *(..)) && @annotation(trace)")
  def trace(pjp:ProceedingJoinPoint, trace:Trace): AnyRef = {
    TraceRecorder.withNewTraceContext(trace.value()) {
      val result = pjp.proceed()
      TraceRecorder.finish()
      result
    }
  }

  @After("execution(@kamon.annotation.Counter * *(..)) && @annotation(counter)")
  def count(counter:Counter):Unit = {
    Kamon(UserMetrics).registerCounter(counter.value()).increment()
  }

  @AfterReturning(pointcut = "execution(@kamon.annotation.Gauge * *(..)) && @annotation(gauge)", returning = "result")
  def count(gauge:Gauge,result:AnyRef):Unit = result match {
    case n:Number => Kamon(UserMetrics).registerGauge(Option(gauge.name()).getOrElse("gauge")){() ⇒ 0L}.record(n.longValue())
    case anythingElse =>
  }
}

object AnnotationBla {
  val system: ActorSystem = ActorSystem("annotations-spec", ConfigFactory.parseString(
    """
      |kamon.metrics {
      |  tick-interval = 1 second
      |  default-collection-context-buffer-size = 100
      |}
    """.stripMargin))
}

