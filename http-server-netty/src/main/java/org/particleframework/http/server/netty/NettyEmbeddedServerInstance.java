/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.http.server.netty;

import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.Prototype;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.discovery.cloud.ComputeInstanceMetadata;
import org.particleframework.discovery.cloud.ComputeInstanceMetadataResolver;
import org.particleframework.discovery.metadata.ServiceInstanceMetadataContributor;
import org.particleframework.runtime.server.EmbeddedServer;
import org.particleframework.runtime.server.EmbeddedServerInstance;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the {@link EmbeddedServerInstance} interface for Netty
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
class NettyEmbeddedServerInstance implements EmbeddedServerInstance {
    private final String id;
    private final NettyHttpServer nettyHttpServer;
    private final Environment environment;
    private final ComputeInstanceMetadataResolver computeInstanceMetadataResolver;
    private final ServiceInstanceMetadataContributor[] metadataContributors;

    private ConvertibleValues<String> instanceMetadata;

    NettyEmbeddedServerInstance(
            @Argument String id,
            @Argument NettyHttpServer nettyHttpServer,
            Environment environment,
            @Nullable ComputeInstanceMetadataResolver computeInstanceMetadataResolver,
            ServiceInstanceMetadataContributor...metadataContributors) {
        this.id = id;
        this.nettyHttpServer = nettyHttpServer;
        this.environment = environment;
        this.computeInstanceMetadataResolver = computeInstanceMetadataResolver;
        this.metadataContributors = metadataContributors;
    }

    @Override
    public EmbeddedServer getEmbeddedServer() {
        return nettyHttpServer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return nettyHttpServer.getURI();
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        if(instanceMetadata == null) {
            Map<String,String> cloudMetadata = new HashMap<>();
            if (computeInstanceMetadataResolver != null) {
                Optional<? extends ComputeInstanceMetadata> resolved = computeInstanceMetadataResolver.resolve(environment);
                if(resolved.isPresent()) {
                    cloudMetadata = resolved.get().getMetadata();
                }
            }
            for (ServiceInstanceMetadataContributor metadataContributor : metadataContributors) {
                metadataContributor.contribute(this, cloudMetadata);
            }
            Map<String, String> metadata = nettyHttpServer.getServerConfiguration()
                    .getApplicationConfiguration()
                    .getInstance()
                    .getMetadata();
            if (cloudMetadata!=null) {
                cloudMetadata.putAll(metadata);
            }
            instanceMetadata = ConvertibleValues.of(cloudMetadata);
        }
        return instanceMetadata;
    }
}