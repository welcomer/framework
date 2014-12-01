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

import scala.math.Ordered.orderingToOrdered

import akka.actor.actorRef2Scala
import akka.event.ActorEventBus
import akka.event.ScanningClassification

private[pico] case class PicoEventedClassifier(
  eventDomain: Option[String],
  eventType: Option[String],
  localOnly: Boolean) extends Ordered[PicoEventedClassifier] {
  def compare(that: PicoEventedClassifier): Int = {
    (this.eventType, this.eventDomain, this.localOnly) compare (that.eventType, that.eventDomain, that.localOnly)
  }
}

private[pico] class PicoEventedEventBusImpl extends ActorEventBus with ScanningClassification {
  type Event = EventedEvent
  type Classifier = PicoEventedClassifier

  override protected def compareClassifiers(a: Classifier, b: Classifier): Int = a.compareTo(b)

  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int = a.compareTo(b)

  override protected def matches(classifier: Classifier, event: Event): Boolean = {
    def checkTypeMatch(classifierType: String): Boolean = (classifierType == event.eventType)
    def checkDomainMatch(classifierDomain: String): Boolean = (classifierDomain == event.eventDomain)
    def checkLocalOnlyMatch: Boolean = {
      if (classifier.localOnly) event.entityId.isEmpty
      else true
    }

    def typeDomainMatch = classifier match {
      case PicoEventedClassifier(Some(d), Some(t), _) => checkDomainMatch(d) && checkTypeMatch(t)
      case PicoEventedClassifier(Some(d), None, _) => checkDomainMatch(d)
      case PicoEventedClassifier(None, Some(t), _) => checkTypeMatch(t)
      case PicoEventedClassifier(None, None, _) => true
    }

    checkLocalOnlyMatch && typeDomainMatch
  }

  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event
  }

  def subscribe(
    subscriber: Subscriber,
    eventDomain: Option[String],
    eventType: Option[String],
    localOnly: Boolean = false): Boolean = {
    subscribe(subscriber, PicoEventedClassifier(eventDomain, eventType, localOnly))
  }

  def unsubscribe(
    subscriber: Subscriber,
    eventDomain: Option[String],
    eventType: Option[String],
    localOnly: Boolean = false): Boolean = {
    unsubscribe(subscriber, PicoEventedClassifier(eventDomain, eventType, localOnly))
  }
}
