package run.halo.links;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;
import static run.halo.app.extension.index.query.QueryFactory.and;
import static run.halo.app.extension.index.query.QueryFactory.contains;
import static run.halo.app.extension.index.query.QueryFactory.equal;
import static run.halo.app.extension.index.query.QueryFactory.or;
import static run.halo.app.extension.router.selector.SelectorUtil.labelAndFieldSelectorToListOptions;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.router.SortableRequest;
import run.halo.app.extension.router.selector.FieldSelector;

@Component
@RequiredArgsConstructor
public class ConsoleLinkRouter {

    private final ReactiveExtensionClient client;
    private final String tag = "plugin-links.halo.run/v1alpha1/ConsoleLink";

    @Bean
    RouterFunction<ServerResponse> consoleLinkRoute() {
        return SpringdocRouteBuilder.route()
            .nest(
                RequestPredicates.path("/apis/plugin-links.halo.run/v1alpha1/console"),
                this::nested
            )
            .build();
    }

    RouterFunction<ServerResponse> nested() {
        return SpringdocRouteBuilder.route()
            .GET("links", this::listLinksForConsole,
                builder -> {
                    builder.operationId("listLinksForConsole")
                        .description("Lists all links for console management (including hidden links)")
                        .tag(tag)
                        .response(responseBuilder()
                            .implementation(ListResult.generateGenericClass(Link.class)));
                    ConsoleLinkQuery.buildParameters(builder);
                }
            )
            .build();
    }

    Mono<ServerResponse> listLinksForConsole(ServerRequest request) {
        ConsoleLinkQuery consoleLinkQuery = new ConsoleLinkQuery(request.exchange());
        return listLinks(consoleLinkQuery)
            .flatMap(links -> ServerResponse.ok().bodyValue(links));
    }

    private Mono<ListResult<Link>> listLinks(ConsoleLinkQuery query) {
        return client.listBy(Link.class, query.toListOptions(), query.toPageRequest());
    }

    static class ConsoleLinkQuery extends SortableRequest {

        public ConsoleLinkQuery(ServerWebExchange exchange) {
            super(exchange);
        }

        @Schema(description = "Keyword to search links under the group")
        public String getKeyword() {
            return queryParams.getFirst("keyword");
        }

        @Schema(description = "Link group name")
        public String getGroupName() {
            return queryParams.getFirst("groupName");
        }

        @Schema(description = "Filter by hidden status (true/false)")
        public Boolean getHidden() {
            String hidden = queryParams.getFirst("hidden");
            return StringUtils.isNotBlank(hidden) ? Boolean.valueOf(hidden) : null;
        }

        @Override
        public ListOptions toListOptions() {
            var listOptions =
                labelAndFieldSelectorToListOptions(getLabelSelector(), getFieldSelector());
            var query = listOptions.getFieldSelector().query();
            if (StringUtils.isNotBlank(getKeyword())) {
                query = and(query, or(
                    contains("spec.displayName", getKeyword()),
                    contains("spec.description", getKeyword()),
                    contains("spec.url", getKeyword())
                ));
            }

            if (StringUtils.isNotBlank(getGroupName())) {
                query = and(query, equal("spec.groupName", getGroupName()));
            }

            if (getHidden() != null) {
                query = and(query, equal("spec.hidden", String.valueOf(getHidden())));
            }

            listOptions.setFieldSelector(FieldSelector.of(query));
            return listOptions;
        }

        @Override
        public Sort getSort() {
            return super.getSort()
                .and(Sort.by(desc("metadata.creationTimestamp"),
                    asc("metadata.name"))
                );
        }

        public static void buildParameters(Builder builder) {
            builder.parameter(parameterBuilder()
                    .name("keyword")
                    .description("Keyword to search links under the group")
                    .in(ParameterIn.QUERY)
                    .implementation(String.class)
                    .required(false)
                )
                .parameter(parameterBuilder()
                    .name("groupName")
                    .description("Link group name")
                    .in(ParameterIn.QUERY)
                    .implementation(String.class)
                    .required(false)
                )
                .parameter(parameterBuilder()
                    .name("hidden")
                    .description("Filter by hidden status (true/false)")
                    .in(ParameterIn.QUERY)
                    .implementation(Boolean.class)
                    .required(false)
                );
            SortableRequest.buildParameters(builder);
        }
    }
}
