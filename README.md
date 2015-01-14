# Welcomer Framework

(Just want to dive in? Check out [Getting Started](#GettingStarted) below)

## What is the Welcomer framework?

The Welcomer framework supports building microservices to flexibly automate your online tasks, putting control of your personal data back in your hands.

* Link to preexisting services/silo's; if it has an API then we can link to it.
* Provide a simple process for consuming events, extracting the relevant details, and reacting to them.

## Why?

The web is plagued by a series of data silos, each holding their own slice of your personal data but not giving you the freedom to access, use and share it as you desire. This trend has been slowly changing for the better in recent year with many services now providing API's to access their data, allowing new applications  to thrive. Unfortunately most of these applications are designed in a rigid way to support a narrowly predefined use. We're all different and our needs and desires for what an application should do vary just as much. The solution is to provide a flexible, open framework that makes it easy to securely and privately use your data as you see fit.

## PROPS

Our approach was to utilise open standards along with concepts from [Phil Windley's](http://www.windley.com/) ['The Live Web'](http://smile.amazon.com/The-Live-Web-Event-Based-Connections/dp/1133686680) to create a Personal, Reactive, Open, Private and Secure framework.

* *Personal*
  * A system running on YOUR behalf, reacting to triggers YOU define, doing what YOU want. Own and control YOUR data.
* *Reactive*
  * Aligned with the [Reactive Manifesto](http://www.reactivemanifesto.org/): Responsive, Resilient, Elastic and Message driven.
* *Open*
  * Open Source: Freely use, modify and contribute to the codebase to suit your needs. Licensed under Apache 2.0
  * Open Design: Create flexible event driven microservices. Don't be restricted by what someone else says you should do.
  * Open Communication: Open engagement with the community to build the best product we can. None of us is as smart as all of us.
  * [TODO: talk openly, develop openly](http://todogroup.org/about/)
* *Private*
  * Adhering to the principles of [Privacy by Design](http://en.wikipedia.org/wiki/Privacy_by_design) and the [Respect Trust Framework](https://www.respectnetwork.com/the-respect-trust-framework/) to keep your data private.
* *Secure*
  * Following industry standards and best practice to ensure a secure system.

## Want to help?

* Got a great idea? Found a nasty bug? Just want to ask a question? Awesome, engage with us at the [issue tracker](https://github.com/welcomer/framework/issues).
* Feel like adding a feature, squashing some bugs, or just tidying up a bit? [Pull requests](https://github.com/welcomer/framework/pulls) are Welcome(r)d with <3

<a name="GettingStarted"></a>

## Getting Started

* Download/install mongodb (https://www.mongodb.org/downloads)
* Download/install typesafe activator (https://typesafe.com/get-started)
* Clone this repository `git clone https://github.com/welcomer/framework.git welcomerFramework`
* Setup initial picos/etc in database `db-init.sh`
* Run the framework `activator run`

## What is a pico?

A [term coined by Phil Windley](http://www.windley.com/archives/2013/10/fundamental_features_of_persistent_compute_objects.shtml), a pico (or persistent compute object) is an individual building block of a personal cloud, having the following characteristics:

* Identity - they represent a specific entity
* Storage - they persistently encapsulate both structured and unstructured data
* Open event network - they respond to events
* Processing - they run applications autonomously
* Event Channels - they have connections to other picos
* APIs - they provide access to and access other online services

A pico can be thought of as a self contained microservice that can accept events, store/manipulate data based on rules, and output events/data.

## Future Ideas / Wishlist

* Command line administration tool
  * Create/delete picos
  * Install rulesets
  * Etc
* Administration ruleset/API?
* Web interface/GUI

## Technologies

* [Scala](http://scala-lang.org/)
* [Akka](http://akka.io/)
* [Spray](http://spray.io/)
* [mongoDB](https://www.mongodb.org/)
* [ReactiveMongo](http://reactivemongo.org/)

## Similar Applications

* [Kinetic Rule Language](http://en.wikipedia.org/wiki/Kinetic_Rule_Language) / [Kinetic Rules Engine](https://github.com/kre/Kinetic-Rules-Engine) / [CloudOS](http://cloudos.me/): Phil Windley and Kynetx's personal cloud architecture.
* [Huginn](https://github.com/cantino/huginn): Build agents that monitor and act on your behalf. Your agents are standing by!
* [IFTTT](http://ifttt.com/): IFTTT is a service that lets you create powerful connections with one simple statement: if this then that.
* [Zapier](https://zapier.com/): Connect your apps, automate your work.
