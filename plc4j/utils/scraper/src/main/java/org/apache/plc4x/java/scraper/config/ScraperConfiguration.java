/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.java.scraper.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.scraper.ScrapeJob;
import org.apache.plc4x.java.scraper.Scraper;

import java.io.*;
import java.security.InvalidParameterException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration class for {@link Scraper}.
 */

public class ScraperConfiguration {

    private final Map<String, String> sources;
    private final List<JobConfiguration> jobConfigurations;

    @JsonCreator
    ScraperConfiguration(@JsonProperty(value = "sources", required = true) Map<String, String> sources,
                         @JsonProperty(value = "jobs", required = true) List<JobConfiguration> jobConfigurations) {
        checkNoUnreferencedSources(sources, jobConfigurations);
        // TODO Warning on too many sources?!
        this.sources = sources;
        this.jobConfigurations = jobConfigurations;
    }

    private void checkNoUnreferencedSources(Map<String, String> sources, List<JobConfiguration> jobConfigurations) {
        Set<String> unreferencedSources = jobConfigurations.stream()
            .flatMap(job -> job.getSources().stream())
            .filter(source -> !sources.containsKey(source))
            .collect(Collectors.toSet());
        if (!unreferencedSources.isEmpty()) {
            throw new PlcRuntimeException("There are the following unreferenced sources: " + unreferencedSources);
        }
    }

    public static ScraperConfiguration fromYaml(String yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            return mapper.readValue(yaml, ScraperConfiguration.class);
        } catch (IOException e) {
            throw new PlcRuntimeException("Unable to parse given yaml configuration!", e);
        }
    }

    public static ScraperConfiguration fromJson(String json) {
        ObjectMapper mapper = new ObjectMapper(new JsonFactory());
        try {
            return mapper.readValue(json, ScraperConfiguration.class);
        } catch (IOException e) {
            throw new PlcRuntimeException("Unable to parse given json configuration!", e);
        }
    }

    public static ScraperConfiguration fromFile(String path) throws IOException {
        ObjectMapper mapper;
        if (path.endsWith("json")) {
            mapper = new ObjectMapper(new JsonFactory());
        } else if (path.endsWith("yml") || path.endsWith("yaml")) {
            mapper = new ObjectMapper(new YAMLFactory());
        } else {
            throw new InvalidParameterException("Only files with extensions json, yml or yaml can be read");
        }
        return mapper.readValue(new File(path), ScraperConfiguration.class);
    }

    public Map<String, String> getSources() {
        return sources;
    }

    public List<JobConfiguration> getJobConfigurations() {
        return jobConfigurations;
    }

    public List<ScrapeJob> getJobs() {
        return jobConfigurations.stream()
            .map(conf -> new ScrapeJob(conf.getName(), conf.getScrapeRate(),
                getSourcesForAliases(conf.getSources()), conf.getFields()))
            .collect(Collectors.toList());
    }

    private Map<String, String> getSourcesForAliases(List<String> aliases) {
        return aliases.stream()
            .collect(Collectors.toMap(
                Function.identity(),
                sources::get
            ));
    }
}
