package ru.megaplan.jira.plugins.workflow.pusher;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.*;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.index.IndexException;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.workflow.IssueWorkflowManager;
import com.atlassian.sal.api.scheduling.PluginJob;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: i.loskutov
 * Date: 06.06.12
 * Time: 12:46
 * To change this template use File | Settings | File Templates.
 */
public class SimplePushJob implements PluginJob {

    private final Logger log = Logger.getLogger(SimplePushJob.class);




    /**
     * Executes this job.
     *
     * @param params any data the job needs to execute. Changes to this data will be remembered between executions.
     */
    @Override
    public void execute(Map<String, Object> params) {
        log.warn("starting job");
        String jobId = (String) params.get(PusherServiceImpl.JOBID);
        log.warn("job id : " + jobId);
        IssueService issueService = (IssueService) params.get("issueService");
        IssueWorkflowManager issueWorkflowManager = (IssueWorkflowManager) params.get("issueWorkflowManager");
        CustomFieldManager customFieldManager = (CustomFieldManager) params.get("customFieldManager");
        IssueManager issueManager = (IssueManager) params.get("issueManager");
        JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        PusherService pusherService = (PusherService) params.get(PusherServiceImpl.ME);

        PusherServiceImpl.Settings settings = (PusherService.Settings) params.get(PusherServiceImpl.SETTINGS);
        String query = settings.getQuery();
        User user = settings.getUser();
        CustomField customField = settings.getCustomField();
        Collection<Issue> issues = pusherService.getIssuesFromQuery(user, query);

        int transitionId = settings.getParam();
        if (issues != null) log.warn(Arrays.toString(issues.toArray()));
        if (issues == null || issues.isEmpty()) return;
        List<IssueService.TransitionValidationResult> results = new ArrayList<IssueService.TransitionValidationResult>();
        boolean transactionIstKaputt = false;
        User oldUser = jiraAuthenticationContext.getLoggedInUser();
        debugCheckOldUser(oldUser, user);
        jiraAuthenticationContext.setLoggedInUser(user);
        for (Issue i : issues) {
            IssueInputParameters empty = issueService.newIssueInputParameters();
            Long id = i.getId();
            IssueService.TransitionValidationResult transitionValidationResult =
                    issueService.validateTransition(user,id,transitionId,empty);



            ErrorCollection ec = transitionValidationResult.getErrorCollection();
            log.warn(Arrays.toString(ec.getErrorMessages().toArray()));
            if (!transitionValidationResult.isValid() || (ec != null && ec.hasAnyErrors())) {

                log.error("can't transition issue : " + i.getKey() + " in transition " + transitionId + " : ");
                        log.error(Arrays.toString(transitionValidationResult.getErrorCollection().getErrorMessages().toArray()) + " AND ");
                        log.error(transitionValidationResult.getErrorCollection().getReasons() + " AND ");
                log.error(Arrays.toString(transitionValidationResult.getErrorCollection().getErrorMessages().toArray()));
                log.error(Arrays.toString(transitionValidationResult.getErrorCollection().getErrors().keySet().toArray()));
                log.error(Arrays.toString(transitionValidationResult.getErrorCollection().getErrors().values().toArray()));
                transactionIstKaputt = true;
                try {
                    ComponentAccessor.getIssueIndexManager().reIndex(i);
                } catch (IndexException e) {
                    log.error(e);
                }
                continue;
            }
            results.add(transitionValidationResult);
        }
        debugCheckOldUser(oldUser, user);
        jiraAuthenticationContext.setLoggedInUser(oldUser);
        if (transactionIstKaputt) {
            //return;
        }
        for (IssueService.TransitionValidationResult validResult : results) {
            log.warn("transition : " + validResult.getIssue().getKey());
            IssueService.IssueResult issueResult = issueService.transition(user, validResult);
            log.warn(Arrays.toString(issueResult.getErrorCollection().getErrorMessages().toArray()));
            log.warn(Arrays.toString(issueResult.getErrorCollection().getErrors().keySet().toArray()));
            log.warn(Arrays.toString(issueResult.getErrorCollection().getErrors().values().toArray()));

            if (customField != null) {
                MutableIssue i = issueResult.getIssue();
                Options opts = ComponentAccessor.getOptionsManager().getOptions(customField.getRelevantConfig(i));
                Option op = null;
                if (opts != null) {
                    for (int j = 0; j < opts.size(); j++) {
                        Option opt = opts.get(j);
                        if (opt.getValue().equals(user.getName())) {
                            op = opt;
                        }
                    }
                }
                if (op != null) {
                    log.warn("setting cf...");
                    i.setCustomFieldValue(customField, op);
                    issueManager.updateIssue(user, i, EventDispatchOption.DO_NOT_DISPATCH, true);
                } else {
                    log.warn("can't find option");
                }

            }
        }
    }

    private void debugCheckOldUser(User oldUser, User user) {
        if (oldUser == null) return;
        if (!oldUser.equals(user)) log.error("OLD USER NOT EQUALS NEW USER WTF");
    }

}
