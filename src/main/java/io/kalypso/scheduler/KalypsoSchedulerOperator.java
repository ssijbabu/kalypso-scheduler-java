package io.kalypso.scheduler;

import io.javaoperatorsdk.operator.Operator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Kalypso Scheduler Operator.
 *
 * This operator is responsible for scheduling applications and services on cluster types
 * and uploading the result to the GitOps repo. It is a Java implementation of the
 * Kubebuilder-based Go operator: https://github.com/microsoft/kalypso-scheduler
 *
 * Features:
 * - Manages high-level control plane abstractions (ClusterType, Workload, Environment, etc.)
 * - Transforms abstractions into low-level Kubernetes manifests
 * - Creates pull requests to GitOps repositories with generated manifests
 * - Integrates with Flux for resource synchronization
 *
 * @author Kalypso Team
 * @version 1.0.0
 */
public class KalypsoSchedulerOperator {

    private static final Logger logger = LoggerFactory.getLogger(KalypsoSchedulerOperator.class);

    public static void main(String[] args) {
        logger.info("Starting Kalypso Scheduler Operator");
        logger.info("Version: 1.0.0");
        logger.info("Framework: java-operator-sdk 5.3.2");

        try {
            Operator operator = new Operator();
            operator.start();
            logger.info("Kalypso Scheduler Operator started successfully");
        } catch (Exception e) {
            logger.error("Failed to start Kalypso Scheduler Operator", e);
            System.exit(1);
        }
    }
}
