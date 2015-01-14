# Changes

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
