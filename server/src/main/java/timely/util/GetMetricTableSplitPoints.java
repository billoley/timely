package timely.util;

import java.util.Map.Entry;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import timely.api.model.Meta;
import timely.configuration.Configuration;
import timely.configuration.SpringBootstrap;

public class GetMetricTableSplitPoints {

    public static void main(String[] args) throws Exception {

        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SpringBootstrap.class)
                .bannerMode(Mode.OFF).web(WebApplicationType.NONE).run(args);
                AccumuloClient accumuloClient = Accumulo.newClient()
                        .from(ctx.getBean(Configuration.class).getAccumulo().getClientProperties()).build();
                Scanner s = accumuloClient.createScanner(ctx.getBean(Configuration.class).getMetaTable(),
                        accumuloClient.securityOperations().getUserAuthorizations(accumuloClient.whoami()))) {

            s.setRange(new Range(Meta.METRIC_PREFIX, true, Meta.TAG_PREFIX, false));
            for (Entry<Key, Value> e : s) {
                System.out.println(e.getKey().getRow().toString().substring(Meta.METRIC_PREFIX.length()));
            }
        }
    }
}
