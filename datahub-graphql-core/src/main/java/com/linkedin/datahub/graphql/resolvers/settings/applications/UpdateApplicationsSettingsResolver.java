package com.linkedin.datahub.graphql.resolvers.settings.applications;

import static com.linkedin.datahub.graphql.resolvers.ResolverUtils.*;

import com.linkedin.datahub.graphql.QueryContext;
import com.linkedin.datahub.graphql.authorization.AuthorizationUtils;
import com.linkedin.datahub.graphql.concurrency.GraphQLConcurrencyUtils;
import com.linkedin.datahub.graphql.exception.AuthorizationException;
import com.linkedin.datahub.graphql.generated.UpdateApplicationsSettingsInput;
import com.linkedin.metadata.service.SettingsService;
import com.linkedin.settings.global.GlobalSettingsInfo;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/** Resolver responsible for updating the actions settings. */
public class UpdateApplicationsSettingsResolver implements DataFetcher<CompletableFuture<Boolean>> {

  private final SettingsService _settingsService;

  public UpdateApplicationsSettingsResolver(@Nonnull final SettingsService settingsService) {
    _settingsService = Objects.requireNonNull(settingsService, "settingsService must not be null");
  }

  @Override
  public CompletableFuture<Boolean> get(final DataFetchingEnvironment environment)
      throws Exception {
    final QueryContext context = environment.getContext();
    final UpdateApplicationsSettingsInput input =
        bindArgument(environment.getArgument("input"), UpdateApplicationsSettingsInput.class);

    return GraphQLConcurrencyUtils.supplyAsync(
        () -> {
          if (AuthorizationUtils.canManageFeatures(context)) {
            try {
              // First, fetch the existing global settings. This does a R-M-F.
              final GlobalSettingsInfo maybeGlobalSettings =
                  _settingsService.getGlobalSettings(context.getOperationContext());

              final GlobalSettingsInfo newGlobalSettings =
                  maybeGlobalSettings != null ? maybeGlobalSettings : new GlobalSettingsInfo();

              final com.linkedin.settings.global.ApplicationsSettings newApplicationsSettings =
                  newGlobalSettings.hasApplications()
                      ? newGlobalSettings.getApplications()
                      : new com.linkedin.settings.global.ApplicationsSettings().setEnabled(false);

              // Next, patch the actions settings.
              updateApplicationsSettings(newApplicationsSettings, input);
              newGlobalSettings.setApplications(newApplicationsSettings);

              // Finally, write back to GMS.
              _settingsService.updateGlobalSettings(
                  context.getOperationContext(), newGlobalSettings);
              return true;
            } catch (Exception e) {
              throw new RuntimeException(
                  String.format("Failed to update action settings! %s", input), e);
            }
          }
          throw new AuthorizationException(
              "Unauthorized to perform this action. Please contact your DataHub administrator.");
        },
        this.getClass().getSimpleName(),
        "get");
  }

  private static void updateApplicationsSettings(
      @Nonnull final com.linkedin.settings.global.ApplicationsSettings settings,
      @Nonnull final UpdateApplicationsSettingsInput input) {
    settings.setEnabled(input.getEnabled());
  }
}
