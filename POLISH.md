# Polish tasks

## To do

- use bot to display current languages
- validate google api token before storing
- quick dispatch to second lambda
   - requires that we make a really small dispatcher jar
- better landing page after authorizing
- do not uses "In" in the message rewrite
- implement oauth state


## Done

- do not cache anything
- condense languages output
- do not mark message as edited even when no translation added
- able to remove all languages from channel 
- welcome message when bot joins
- do not respond to commands when bot is not in channel
   - let user know to invite the bot
- do not store client secret in code
- correctly parse messages  (This is not completely solved yet, what we really need is a way to stop Google from translating the special strings)
  - links
  - emoji
- attribute Google Translate

