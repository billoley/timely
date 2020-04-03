package timely.store;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.accumulo.core.client.AccumuloClient;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import timely.api.model.Meta;
import timely.configuration.Configuration;

public class MetaCacheImpl implements MetaCache {

    private static Logger LOG = LoggerFactory.getLogger(MetaCacheImpl.class);
    private static final Object DUMMY = new Object();
    private volatile boolean closed = false;
    private Cache<Meta, Object> cache = null;
    private Configuration configuration;
    private Timer timer = new Timer("MetaCacheImpl", true);

    @Override
    public void init(Configuration config) {
        configuration = config;
        cache = Caffeine.newBuilder().initialCapacity(1000).build();
        long cacheRefreshMinutes = configuration.getMetaCache().getCacheRefreshMinutes();
        if (cacheRefreshMinutes > 0) {
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    refreshCache(config.getMetaCache().getExpirationMinutes());
                }
            }, 60000, cacheRefreshMinutes * 60 * 1000);
        }
    }

    private void refreshCache(long expirationMinutes) {
        long oldestTimestamp = System.currentTimeMillis() - (expirationMinutes * 60 * 1000);
        String metaTable = configuration.getMetaTable();
        Map<String, Map<String, Long>> metricMap = new HashMap<>();
        try (AccumuloClient accumuloClient = org.apache.accumulo.core.client.Accumulo.newClient()
                .from(configuration.getAccumulo().getClientProperties()).build();
                Scanner scanner = accumuloClient.createScanner(metaTable, Authorizations.EMPTY)) {
            Map<Meta, Object> newCache = new HashMap<>();
            LOG.debug("Begin scanning " + metaTable);
            Range metricRange = new Range(new Key(Meta.METRIC_PREFIX), true, new Key(Meta.METRIC_PREFIX + '\0'), false);
            scanner.setRange(metricRange);
            Set<String> allMetrics = new TreeSet<>();
            for (Map.Entry<Key, Value> entry : scanner) {
                Meta meta = Meta.parse(entry.getKey(), entry.getValue(), Meta.METRIC_PREFIX);
                allMetrics.add(meta.getMetric());
            }
            for (String currMetric : allMetrics) {
                Key begin = new Key(Meta.VALUE_PREFIX + currMetric);
                Key end = new Key(Meta.VALUE_PREFIX + currMetric + '\0');
                Range range = new Range(begin, true, end, false);
                scanner.setRange(range);
                Iterator<Map.Entry<Key, Value>> itr = scanner.iterator();
                boolean maxedOutValues = false;
                while (itr.hasNext() && !maxedOutValues) {
                    Map.Entry<Key, Value> entry = itr.next();
                    Meta meta = Meta.parse(entry.getKey(), entry.getValue(), Meta.VALUE_PREFIX);
                    String metric = meta.getMetric();
                    String tagKey = meta.getTagKey();
                    Map<String, Long> tagMap = metricMap.get(metric);
                    if (tagMap == null) {
                        tagMap = new HashMap<>();
                        metricMap.put(metric, tagMap);
                    }
                    long numTagValues = tagMap.getOrDefault(tagKey, 0l);
                    if (entry.getKey().getTimestamp() > oldestTimestamp) {
                        tagMap.put(tagKey, ++numTagValues);
                        if (numTagValues <= configuration.getMetaCache().getMaxTagValues()) {
                            newCache.put(meta, DUMMY);
                        } else {
                            // found maxTagValues on this refresh
                            maxedOutValues = true;
                        }
                    }
                }
            }
            synchronized (cache) {
                cache.invalidateAll();
                cache.putAll(newCache);
            }
            LOG.debug("Finished scanning " + metaTable);
        } catch (TableNotFoundException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Override
    public void add(Meta meta) {
        cache.put(meta, DUMMY);
    }

    @Override
    public boolean contains(Meta meta) {
        return cache.asMap().containsKey(meta);
    }

    @Override
    public void addAll(Collection<Meta> c) {
        c.forEach(m -> cache.put(m, DUMMY));
    }

    @Override
    public Iterator<Meta> iterator() {
        return cache.asMap().keySet().iterator();
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }
}
