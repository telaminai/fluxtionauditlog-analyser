package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateClient;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;

/**
 * Option 2: where the analyser's starter templates come from — the configured HTTPS origin ({@link TemplateClient}), and
 * only from the starter effects' performers. A frame test puts a stub in; no test touches the network.
 */
interface StarterSource {

    TemplateCatalogue.Selection catalogue();

    TemplateClient.Template template(TemplateCatalogue.Entry entry);

    byte[] download(TemplateClient.Download request);

    /** The host the review row names ("Downloads from …"), with the override said when one is in force. */
    String origin();

    /** The playground over HTTPS (or the loopback override the client allows), read at each call. */
    static StarterSource playground() {
        return new StarterSource() {
            private TemplateClient client;

            private synchronized TemplateClient client() {
                if (client == null) client = TemplateClient.playground();
                return client;
            }

            @Override
            public TemplateCatalogue.Selection catalogue() {
                return client().catalogue(ReleaseNotes.version());
            }

            @Override
            public TemplateClient.Template template(TemplateCatalogue.Entry entry) {
                return client().template(entry);
            }

            @Override
            public byte[] download(TemplateClient.Download request) {
                return client().download(request);
            }

            @Override
            public String origin() {
                try {
                    String host = TemplateClient.configuredOrigin().getHost();
                    return TemplateClient.originOverridden() ? host + " (-D" + TemplateClient.ORIGIN_PROPERTY
                            + ", not the Fluxtion playground)" : host;
                } catch (RuntimeException e) {
                    return "an origin the analyser cannot use (" + e.getMessage() + ")";
                }
            }
        };
    }
}
