# Backlog

- HIGH PRIORITY
    - improve cold-start responsiveness
         - maybe post requests to a Kinesis-based queue?
         - maybe create a lighter dispatcher Lambda Function that invokes the real one?
         - maybe use something other than Java?

- MOVE FROM PROTOTYPE
    - now that this is prototyped and feedback is coming in, consider restarting the design
      under TDD and making it a properly maintained application. 
         - so far feedback pretty positive on my Slack teams
         - hopefully some feedback from the AWS competition

- NOT SO HIGH
    - implement oauth state
    - correctly deal with the Google Translate API translating special sequences such as links and emoji. The current solution is hacky and does not catch everything
    - ask a proper designer to make the pages
    - use bot instead of command output to display current languages


