package timely.store.compaction.util;

import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.AccumuloClient;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import timely.configuration.Configuration;
import timely.configuration.SpringBootstrap;

public class TabletMetadataConsole {

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SpringBootstrap.class)
                .bannerMode(Banner.Mode.OFF).web(WebApplicationType.NONE).run(args)) {
            Configuration conf = ctx.getBean(Configuration.class);
            try (AccumuloClient accumuloClient = org.apache.accumulo.core.client.Accumulo.newClient()
                    .from(conf.getAccumulo().getClientProperties()).build()) {
                TabletMetadataQuery query = new TabletMetadataQuery(accumuloClient, conf.getMetricsTable());
                TabletMetadataView view = query.run();
                System.out.println(view.toText(TimeUnit.DAYS));
            }
        }
    }
}
