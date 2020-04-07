package com.salaboy.zeebe.knative;

import io.cloudevents.extensions.ExtensionFormat;
import io.cloudevents.extensions.InMemoryFormat;

import java.util.*;

public class ZeebeCloudEventExtension {
    private String correlationKey;

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZeebeCloudEventExtension that = (ZeebeCloudEventExtension) o;
        return Objects.equals(correlationKey, that.correlationKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correlationKey);
    }

    public static class Format implements ExtensionFormat {
        public static final String IN_MEMORY_KEY = "zeebe";
        public static final String CORRELATION_KEY = "correlationKey";

        private final InMemoryFormat memory;
        private final Map<String, String> transport = new HashMap<>();
        public Format(ZeebeCloudEventExtension extension) {
            Objects.requireNonNull(extension);

            memory = InMemoryFormat.of(IN_MEMORY_KEY, extension,
                    ZeebeCloudEventExtension.class);
            transport.put(CORRELATION_KEY, extension.getCorrelationKey());

        }

        @Override
        public InMemoryFormat memory() {
            return memory;
        }

        @Override
        public Map<String, String> transport() {
            return transport;
        }

        public static Optional<ExtensionFormat> unmarshall(
                Map<String, String> exts) {
            String correlationKey = exts.get(Format.CORRELATION_KEY);


            if(null!= correlationKey) {
                ZeebeCloudEventExtension zcee = new ZeebeCloudEventExtension();
                zcee.setCorrelationKey(correlationKey);


                InMemoryFormat inMemory =
                        InMemoryFormat.of(Format.IN_MEMORY_KEY, zcee,
                                ZeebeCloudEventExtension.class);

                return Optional.of(
                        ExtensionFormat.of(inMemory,
                                new AbstractMap.SimpleEntry<>(Format.CORRELATION_KEY, correlationKey)
                                )
                );

            }

            return Optional.empty();
        }
    }
}
