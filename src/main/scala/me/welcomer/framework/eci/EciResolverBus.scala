package me.welcomer.framework.eci

import akka.event.EventBus
import akka.event.LookupClassification
import akka.event.ActorEventBus
import me.welcomer.framework.eci.EciResolver.EciResolverMsg

//final case class MsgEnvelope(topic: String, payload: Any)

/**
 * Publishes the payload of the MsgEnvelope when the topic of the
 * MsgEnvelope equals the String specified when subscribing.
 */
private[eci] class EciResolverBusImpl(initialMapSize: Int) extends ActorEventBus with LookupClassification {
  type Event = EciResolverMsg
  type Classifier = String

  // is used for extracting the classifier from the incoming events  
  override protected def classify(event: Event): Classifier = event.eci

  // will be invoked for each event for all subscribers which registered themselves
  // for the event’s classifier
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  // determines the initial size of the index data structure
  // used internally (i.e. the expected number of different classifiers)
  override protected def mapSize: Int = initialMapSize

}