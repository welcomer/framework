# Changes

* 2015-05-21 v0.1.4.3
  * WOH, those files aren't meant to be there.. Also, all release packages before this version have been revoked (and the keys in them deleted/reset)
* 2015-05-21 v0.1.4.2
  * Cleanup some unused dependencies causing issues
* 2015-05-20 v0.1.4.1
  * Releases now available on Bintray! (from this release onwards)
* 2015-05-20 v0.1.4
  * **BREAKING CHANGES:**
    * There are SO many changes in this release, and since we are still in the early 0.x releases the API is *definitely* not stable yet. There are bound to be breaking changes in this release. Sorry :(
  * **Ruleset patterns**
    * *ChannelMapping*: Added retrieveOrMapNewPico, raiseRemoteEventedFunctionForChannel, checkMappedEci, validateMappedEci, etc + updated example ruleset
  * **Evented**
    * *EventedResultNg*: The next generation of EventedResult. Now with more scalaz monady goodness, nicer conversions, improved for-comp support, etc. The future is now!
    * Respect EventedFunction timeout
    * Various other tweaks and improvements
  * **ExternalEventedGateway**
    * Added GET /v1/event endpoint
    * Support for 'PermissionDenied' errors -> HTTP status 'Unauthorized'
    * CORS support
    * Timeout handling improvements
    * Can now properly return JsArray's
  * **Links**
    * *Xero*: Many changes, improvements and refactorings including 'public app' (oauth) support
    * *Mandrill*: Tweaks/improvements
    * *AWS*: Key Mangement Service, Encryption, Etc
    * *Encryption*: Encryption helpers (including AES256)
  * **Pico**
    * Lift ruleset EventedFunction exceptions into EventedFailure's
    * Refactored DSL into seperate files
    * Added handleFunction to DSL
  * **PicoPdsService**
    * Added filter to retrieveAllNamespaces
    * Added selector to storeItemWithJsValue
    * Added pushScopedArrayItems
  * **PicoContainer**
    * Workaround to only use 2sec delay hack when starting new pico's (still need to fix this properly)
  * **Misc**
    * Added Signpost Spray OAUTH support
    * Improve logging output
    * Refactored framework into smaller subprojects
    * Fixed the deprecated deprecated warnings! (finally)
    * Bug fixes, tweaks, cleanup, improvements and everything else I forgot about!
* 2015-01-14 v0.1.3
  * **Ruleset patterns**
    * Allow you to easily make use of common patterns in your rulesets without having to write tons of code! (`me.welcomer.framework.pico.ruleset.patterns`)
    * *ChannelMapping*: Allows you to map a channel type/id to an ECI, create new picos, forward events, and more!
    * *TransactionIdMapping*: Use the transactionId from an event to remember who we should reply to.
  * **Rulesets**
    * Example rulesets now stored in `me.welcomer.rulesets.example`
    * *PersonalCloud* (`me.welcomer.rulesets.personalCloud`): Store/retrieve data for the specified user
  * **EventedFunction**
    * Improved async (futures) support
    * You can now forward EventedFunctions to be handled by another pico/ruleset (See *ChannelMapping*)
    * Added POST route to the ExternalEventedGateway to support more complex module/function calls.
  * `db-init.sh` script to setup some example pico's/data
  * Bug fixes, tweaks and cleanup
* 2015-01-07 v0.1.2
  * Added ruleset/module 'function' calls (incl from external web api)
  * Refactored 'link' services to be cleaner + added basic [Xero](www.xero.com) link
  * Bug fixes, tweaks and cleanup
* 2014-12-03 v0.1.1: Fix some bugs that crept through..
* 2014-12-01 v0.1.0: Initial v0.1 public release of the Welcomer Framework!!
