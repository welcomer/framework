/*  Copyright 2014 White Label Personal Clouds Pty Ltd
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package me.welcomer.framework.pico

import java.util.Date

import scala.math.Ordered.orderingToOrdered

import play.api.libs.functional.syntax._
import play.api.libs.json._

// Timestamp should be 'HTTP Date'
case class EventedEvent(
  eventDomain: String,
  eventType: String,
  timestamp: Option[Date] = Some(new Date()), // TODO: Should we shift this after attributes (or to the very end?) since it's less likely to be manually set?
  attributes: JsObject = Json.obj(), // TODO: Should we add apply() helper methods that accepts a Map[String, String] or Set/List((String, String))
  entityId: Option[String] = None) extends Ordered[EventedEvent] {

  def withEntityId(newEntityId: String) = this.copy(entityId = Option(newEntityId))
  def withNoEntityId = this.copy(entityId = None)

  def compare(that: EventedEvent): Int = {
    import scala.math.Ordered.orderingToOrdered

    (this.timestamp, this.eventDomain, this.eventType) compare (that.timestamp, that.eventDomain, that.eventType)
  }

  override def toString: String = {
    val attributesToString = Json.stringify(attributes)

    s"EventedEvent($eventDomain,$eventType,$timestamp,Attributes($attributesToString),$entityId)"
  }
}

object EventedEvent {
  implicit lazy val eventedEventFormat = (
    (__ \ "_domain").format[String] ~
    (__ \ "_type").format[String] ~
    (__ \ "_timestamp").formatNullable[Date] ~
    (__ \ "_attr").format[JsObject] ~
    (__ \ "_entityId").formatNullable[String])(EventedEvent.apply, unlift(EventedEvent.unapply))
}
//
//  override def isSubclass(x: EventedClassifier, y: EventedClassifier): Boolean = {
//    x.entityId == y.entityId && x.eventType == y.eventType
//  }
//
//}
//
///**
// * @see http://doc.akka.io/docs/akka/2.3.5/scala/event-bus.html
// * @see https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/event/EventBus.scala
// */
//class EventedEventBusImpl extends ActorEventBus with SubchannelClassification {
//  // TODO: Implement own version of SubchannelClassification that restricts scoped subscription (to pico/ruleset/rule type thing)
//  // Do it as a mixin? (defines type Scope, def CheckScope(subscriber):Boolean, subscribe that calls super.subscribe if scope matches)
//  // https://github.com/akka/akka/blob/master/akka-actor/src/main/scala/akka/event/EventBus.scala
//  type Event = EventedEvent
//  type Classifier = EventedClassifier
//
//  // Subclassification is an object providing `isEqual` and `isSubclass`
//  // to be consumed by the other methods of this classifier
//  override protected val subclassification: Subclassification[Classifier] = new EventedSubclassification
//
//  override protected def classify(event: Event): Classifier = EventedClassifier(event.entityId, event.eventType, event.eventDomain)
//
//  // will be invoked for each event for all subscribers which registered
//  // themselves for the event’s classifier
//  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
//    subscriber ! event
//  }
//}
