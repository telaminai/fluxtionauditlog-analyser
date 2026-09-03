your job is to design the spec for a fluxtion log analyser app with these requirements.

- write the spec and tracker here /Users/greghiggins/IdeaProjects/acme/auditloganalyser/analyser/docs/specs
- the ability to analyse fluxtion logs because they are machine readable
- the logs are in a file and are attempt to be standard yaml
- The parse must be lenient and make best effort to parse/render a log entry
- each log entry is separated by a --- yaml separator
- sample log file /Users/greghiggins/IdeaProjects/acme/auditloganalyser/analyser/docs/specs/sample.yml
- the ui will be able to handle log files upto 500MB in memory after that pointers and file mapping will be needed
- The ui will be swing so no need for a native app or dependencies on anything else
- the user will be able to set in a config panel
  - The log file to analyse
  - The source roots for java projects - market-maker-lib, trade-calculator-api-lib, trade-calculator-impl-lib
  - Set a key for claude or openai api keys and select the model to use
  - The event processor this relates to seed from initially /Users/greghiggins/IdeaProjects/market-maker-lib/src/main/java/com/acme/marketmaker/strategy
  - Add the ability to add a FQN for an event processor
  - The default eventprocessor FQN is com.acme.marketmaker.strategy.DemoMarketMakerStrategy
- The logs will be rendered in a jtable with a row for each eventLogRecord and the columns will be
  - eventTime, render as a date/time
  - logTime, render as a date/time
  - groupingId
  - event
  - eventToString
  - thread
  - nodeLogs
  - endTime, render as a date/time
- Clicking a log will show the full nodeLogs entry for that eventLogRecord in a text area that is coloured yaml to make it easier to read
- Filtering on log time using a draggable date-time range picker
- Filering by event type will be supported use the pair
    event: ExportFunctionAuditEvent
    eventToString: public boolean com.acme.tradecalculator.api.lib.node.hedging.VenueHedgeMonitorCalculator.orderVenueConnected(com.fluxtion.server.plugin.trading.service.order.OrderVenueConnectedEvent)
    to form the filter, if eventToString is a method signature then use that to build grouping by callback type in the case above orderVenueConnected otherwise it is a plain event type
- Provide a summary table of the logs grouped by event type and event time range
- Provide a panel where a user can ask for an explanation of an eventLogRecord sent to an LLM the context is
  - the full nodeLogs entry for that eventLogRecord or records if selected
  - an explanation to the llm how fluxtion eventLogRecord is structured, it is a key/value pair from a node in a graph
  - the source root nodes (initially market-maker-lib, trade-calculator-api-lib, trade-calculator-impl-lib) give the context for the nodeLogs entry
  - I am not sure if a skill is neeeded for the llm interaction or you seed the prompt programmatically
- The UI will provide a two way conversation with the LLM to ask questions about the logs it is analyzing
- The UI will provide a reset conversation button
- If no api key is provided then the UI will provide the prompt for the user to copy and paste into an  LLM, I am not sure if a skill is needed or the prompt is enough
- Provide the ability to graph a a property from a node logs entry, each node log entry is the name of the java instance in the event processor, so a key the pair instanceId + key in the nodeLogs entry
  eg for hedgeToOrdersNode: { hedgeQuantity: 0.0} the key is hedgeToOrdersNode.hedgeQuantity
- A help page that explains the UI and the LLM interaction, what fluxtion audit logs are and how to interpret them
- If all the source roots are provided then the UI can open the relevant source file in a non-editable text area with coloring of the source code, the current EventProcessor must be selected
- Anything else you think is useful
