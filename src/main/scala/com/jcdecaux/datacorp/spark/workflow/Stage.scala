package com.jcdecaux.datacorp.spark.workflow

import com.jcdecaux.datacorp.spark.annotation.InterfaceStability
import com.jcdecaux.datacorp.spark.exception.AlreadyExistsException
import com.jcdecaux.datacorp.spark.internal.{HasDescription, HasUUIDRegistry, Identifiable, Logging}
import com.jcdecaux.datacorp.spark.transformation.{Deliverable, Factory, FactoryDeliveryMetadata, FactoryOutput}

import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.mutable.ParArray

@InterfaceStability.Evolving
class Stage extends Logging with Identifiable with HasUUIDRegistry with HasDescription {

  private[this] var _end: Boolean = true

  private[this] var _parallel: Boolean = true

  private[this] var _stageId: Int = _

  val factories: ArrayBuffer[Factory[_]] = ArrayBuffer()

  var deliveries: Array[Deliverable[_]] = _

  private[workflow] def end: Boolean = _end

  private[workflow] def end_=(value: Boolean): Unit = {
    _end = value
  }

  private[workflow] def start: Boolean = if (stageId == 0) true else false

  private[workflow] def stageId: Int = _stageId

  private[workflow] def setStageId(id: Int): this.type = {
    _stageId = id
    this
  }

  def parallel: Boolean = _parallel

  def parallel_=(boo: Boolean): Unit = {
    _parallel = boo
  }

  @throws[IllegalArgumentException]("Exception will be thrown if the length of constructor arguments are not correct")
  def addFactory(factory: Class[_ <: Factory[_]], constructorArgs: Object*): this.type = {

    val primaryConstructor = factory.getConstructors.head

    val newFactory = if (primaryConstructor.getParameterCount == 0) {
      primaryConstructor.newInstance()
    } else {
      primaryConstructor.newInstance(constructorArgs: _*)
    }

    addFactory(newFactory.asInstanceOf[Factory[_]])
  }

  @throws[AlreadyExistsException]
  def addFactory(factory: Factory[_]): this.type = {
    if (registerNewItem(factory)) {
      factories += factory
    } else {
      throw new AlreadyExistsException(s"The current factory ${factory.getCanonicalName} (${factory.getUUID.toString})" +
        s"already exists")
    }
    this
  }

  override def describe(): this.type = {
    log.info(s"Stage $stageId contains ${factories.length} factories")
    factories.foreach(_.describe())
    this
  }

  def run(): this.type = {
    deliveries = parallelFactories match {
      case Left(par) =>
        log.debug(s"Stage $stageId will be run in parallel mode")
        par.map(runFactory).toArray

      case Right(nonpar) =>
        log.debug(s"Stage $stageId will be run in sequential mode")
        nonpar.map(runFactory)
    }
    this
  }

  private[this] val runFactory: Factory[_] => Deliverable[_] = {
    factory: Factory[_] => factory.read().process().write().getDelivery
  }

  private[this] def parallelFactories: Either[ParArray[Factory[_]], Array[Factory[_]]] = {
    if (_parallel) {
      Left(factories.par)
    } else {
      Right(factories.toArray)
    }
  }

  private[workflow] def createDAGNodes(): Array[Node] = {

    factories.map {
      fac =>
        val setter = FactoryDeliveryMetadata.builder().setFactory(fac).getOrCreate()
        val output = FactoryOutput(runtimeType = fac.deliveryType(), consumer = fac.consumers)

        Node(fac.getClass, fac.getUUID, this.stageId, setter.toList, output)
    }.toArray
  }

}
