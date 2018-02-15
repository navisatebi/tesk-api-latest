package uk.ac.ebi.tsc.tesk.util.component;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uk.ac.ebi.tsc.tesk.exception.KubernetesException;
import uk.ac.ebi.tsc.tesk.exception.TaskNotFoundException;

import java.util.StringJoiner;
import java.util.stream.Collectors;

import static uk.ac.ebi.tsc.tesk.util.constant.Constants.*;

/**
 * @author Ania Niewielska <aniewielska@ebi.ac.uk>
 */
@Component
public class KubernetesClientWrapper {

    private static Logger logger = LoggerFactory.getLogger(KubernetesClientWrapper.class);

    private final BatchV1Api batchApi;

    private final BatchV1Api patchBatchApi;

    private final CoreV1Api coreApi;

    private final CoreV1Api patchCoreApi;

    private final String namespace;

    public KubernetesClientWrapper(BatchV1Api batchApi, @Qualifier("patchBatchApi") BatchV1Api patchBatchApi,
                                   CoreV1Api coreApi, @Qualifier("patchCoreApi") CoreV1Api patchCoreApi,
                                   @Value("${tesk.api.k8s.namespace}") String namespace) {
        this.batchApi = batchApi;
        this.patchBatchApi = patchBatchApi;
        this.coreApi = coreApi;
        this.patchCoreApi = patchCoreApi;
        this.namespace = namespace;
    }

    public V1Job createJob(V1Job job) {
        try {
            return this.batchApi.createNamespacedJob(namespace, job, null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

    public V1Job readTaskmasterJob(String taskId) {
        try {
            V1Job job = this.batchApi.readNamespacedJob(taskId, namespace, null, null, null);
            if (job.getMetadata().getLabels().entrySet().stream().anyMatch(entry -> LABEL_JOBTYPE_KEY.equals(entry.getKey()) && LABEL_JOBTYPE_VALUE_TASKM.equals(entry.getValue())))
                return job;
        } catch (ApiException e) {
            if (e.getCode() != HttpStatus.NOT_FOUND.value())
                throw KubernetesException.fromApiException(e);
        }
        throw new TaskNotFoundException(taskId);
    }

    public V1JobList listJobs(String _continue, String labelSelector, Integer limit) {
        try {
            return this.batchApi.listNamespacedJob(namespace, null, _continue, null, null, labelSelector, limit, null, null, null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

    public V1JobList listAllTaskmasterJobs(String pageToken, Integer itemsPerPage) {
        String labelSelector = new StringJoiner("=").add(LABEL_JOBTYPE_KEY).add(LABEL_JOBTYPE_VALUE_TASKM).toString();
        return this.listJobs(pageToken, labelSelector, itemsPerPage);
    }

    public V1JobList listSingleTaskExecutorJobs(String taskId) {
        String labelSelector = new StringJoiner("=").add(LABEL_TESTASK_ID_KEY).add(taskId).toString();
        return this.listJobs(null, labelSelector, null);
    }

    public V1JobList listAllTaskExecutorJobs() {
        String labelSelector = new StringJoiner("=").add(LABEL_JOBTYPE_KEY).add(LABEL_JOBTYPE_VALUE_EXEC).toString();
        return this.listJobs(null, labelSelector, null);
    }

    public V1PodList listSingleJobPods(V1Job job) {
        String labelSelector = job.getSpec().getSelector().getMatchLabels().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(","));
        try {
            return this.coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, null, null, null, null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

    public V1PodList listAllJobPods() {
        String labelSelector = "job-name";
        try {
            return this.coreApi.listNamespacedPod(namespace, null, null, null, null, labelSelector, null, null, null, null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

    public String readPodLog(String podName) {
        try {
            return this.coreApi.readNamespacedPodLog(podName, namespace, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            logger.info("Getting logs for pod " + podName + " failed.", e);
        }
        return null;
    }


    public void labelJobAsCancelled(String taskId) {
        try {
            //TODO - pick label; move patch to constant
            this.patchBatchApi.patchNamespacedJob(taskId, namespace, new V1Job().metadata(new V1ObjectMeta().putLabelsItem("task-status", "Cancelled")), null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

    public void labelPodAsCancelled(String podName) {
        try {
            this.patchCoreApi.patchNamespacedPod(podName, namespace, new V1Pod().metadata(new V1ObjectMeta().putLabelsItem("task-status", "Cancelled")), null);
        } catch (ApiException e) {
            throw KubernetesException.fromApiException(e);
        }
    }

}
