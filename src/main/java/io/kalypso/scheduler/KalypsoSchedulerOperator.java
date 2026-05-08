package io.kalypso.scheduler;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.kalypso.scheduler.controllers.BaseRepoReconciler;
import io.kalypso.scheduler.controllers.EnvironmentReconciler;
import io.kalypso.scheduler.services.FluxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Main entry point for the Kalypso Scheduler Operator.
 *
 * <p>This operator is a Java port of the Kubebuilder-based Go operator at
 * https://github.com/microsoft/kalypso-scheduler. It manages high-level control-plane
 * abstractions (ClusterType, Workload, Environment, etc.), transforms them into Kubernetes
 * manifests, and delivers those manifests to GitOps repositories via Flux and GitHub PRs.
 *
 * <p>Reconcilers are registered via {@link #reconcilers()}. The operator starts in
 * <em>passive mode</em> when that list is empty (early migration days, CRDs only), and
 * switches to active reconciliation mode once at least one controller is registered.
 */
public class KalypsoSchedulerOperator {

    private static final Logger logger = LoggerFactory.getLogger(KalypsoSchedulerOperator.class);

    public static void main(String[] args) {
        logger.info("Starting Kalypso Scheduler Operator");
        logger.info("Version: 1.0.0");
        logger.info("Framework: java-operator-sdk 5.3.2");

        try {
            List<Reconciler<?>> reconcilers = reconcilers();

            if (reconcilers.isEmpty()) {
                // JOSDK 5.x rejects operator.start() with no controllers registered.
                // During early migration days (Days 1-7) no controllers exist yet, so we
                // run in passive mode: the Pod stays alive for health checks and CRD validation
                // but performs no reconciliation.
                logger.info("No reconcilers registered — running in passive mode (CRDs installed, no active reconciliation)");
                logger.info("Kalypso Scheduler Operator started successfully");
                Thread.currentThread().join();
                return;
            }

            Operator operator = new Operator();
            reconcilers.forEach(operator::register);
            operator.installShutdownHook(Duration.ofSeconds(30));
            operator.start();
            logger.info("Kalypso Scheduler Operator started successfully");
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            logger.info("Kalypso Scheduler Operator shutting down");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start Kalypso Scheduler Operator", e);
            System.exit(1);
        }
    }

    /**
     * Returns the list of reconcilers to register with the operator.
     *
     * <p>Add entries here as each day's controllers are implemented.
     * The operator starts in passive mode when this list is empty.
     *
     * @return reconcilers to register
     */
    private static List<Reconciler<?>> reconcilers() {
        KubernetesClient client = new KubernetesClientBuilder().build();
        FluxService fluxService = new FluxService(client);

        return List.of(
            new BaseRepoReconciler(fluxService),
            new EnvironmentReconciler(client, fluxService)
            // Day 9:  new WorkloadRegistrationReconciler(...)
            // Day 9:  new WorkloadReconciler(...)
            // Day 10: new SchedulingPolicyReconciler(...)
            // Day 11: new AssignmentReconciler(...)
            // Day 12: new AssignmentPackageReconciler(...)
            // Day 12: new GitOpsRepoReconciler(...)
        );
    }
}
