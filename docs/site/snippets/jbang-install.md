<!-- The one copy of the JBang install commands: included by index.md, install.md and getting-started.md. Commands are
     JBang's own (https://www.jbang.dev/download/); change them here, never in a page. -->
=== "macOS / Linux"

    ```bash
    curl -Ls https://sh.jbang.dev | bash -s - app setup
    ```

    or, on macOS with Homebrew: `brew install jbangdev/tap/jbang`

=== "Windows (PowerShell)"

    ```powershell
    iex "& { $(iwr https://ps.jbang.dev) } app setup"
    ```

    or `choco install jbang`, or with Scoop: `scoop bucket add jbangdev https://github.com/jbangdev/scoop-bucket`,
    then `scoop install jbang`
