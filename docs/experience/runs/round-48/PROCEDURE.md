# The procedure — six steps, in this order

1. **Read all five manifests in one command.** `for j in lib/*.jar; do unzip -p $j META-INF/MANIFEST.MF; done`
2. **List the figures the business requires**, then mark which component provides each. Where a jar
   offers two, the larger one is only needed if a required figure or a required interface demands it.
3. **Check the `Fluxtion-Requires` chain closes.** If a component you chose requires an interface no
   chosen component publishes, you have picked the wrong variant upstream.
4. **One `javap` per selected entry point** to get its public field names. Nothing else needs `javap`.
5. **Write the bean file.** One bean per component; cross-component arguments are
   `value="#{bean.field}"`, never `ref=`.
6. **Write the runner, then `mvn -q -o test`.**

If a build fails, do not explore — re-read the manifest for the component named in the error.
