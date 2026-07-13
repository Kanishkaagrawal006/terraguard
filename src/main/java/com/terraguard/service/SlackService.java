package com.terraguard.service;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.request.chat.ChatUpdateRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.terraguard.config.AppProperties;
import com.terraguard.model.Risk;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

@Service
public class SlackService {

    private final AppProperties props;
    private final MethodsClient methods;

    public SlackService(AppProperties props) {
        this.props = props;
        this.methods = Slack.getInstance().methods(props.getSlack().getBotToken());
    }

    /** Posts the initial review message with Approve/Reject/View Full Plan buttons. */
    public ChatPostMessageResponse postReviewMessage(String channel, String prTitle, int prNumber,
                                                       String repo, String summary, List<Risk> risks,
                                                       String planUrl, String buttonValue) throws Exception {
        List<LayoutBlock> blocks = buildReviewBlocks(prTitle, prNumber, repo, summary, risks, planUrl, buttonValue);

        ChatPostMessageRequest req = ChatPostMessageRequest.builder()
                .channel(channel)
                .text("Infrastructure review: PR #" + prNumber + " in " + repo) // fallback for notifications
                .blocks(blocks)
                .build();

        ChatPostMessageResponse resp = methods.chatPostMessage(req);
        if (!resp.isOk()) {
            throw new IllegalStateException("Slack post failed: " + resp.getError());
        }
        return resp;
    }

    /** Replaces the buttons with a static outcome line once a decision is made. */
    public void updateWithOutcome(String channel, String ts, String outcomeText) throws Exception {
        List<LayoutBlock> blocks = List.of(
                section(s -> s.text(markdownText(outcomeText)))
        );
        ChatUpdateRequest req = ChatUpdateRequest.builder()
                .channel(channel)
                .ts(ts)
                .text(outcomeText)
                .blocks(blocks)
                .build();
        methods.chatUpdate(req);
    }

    /** Opens the "reason for rejection" modal, triggered from the Reject button click. */
    public void openRejectReasonModal(String triggerId, String privateMetadata) throws Exception {
        View view = view(v -> v
                .type("modal")
                .callbackId("reject_reason_submit")
                .privateMetadata(privateMetadata)
                .title(viewTitle(t -> t.type("plain_text").text("Reject PR")))
                .submit(viewSubmit(s -> s.type("plain_text").text("Submit")))
                .close(viewClose(c -> c.type("plain_text").text("Cancel")))
                .blocks(asBlocks(
                        input(i -> i
                                .blockId("reason_block")
                                .label(plainText("Reason for rejection"))
                                .element(plainTextInput(pt -> pt.actionId("reason_input").multiline(true)))
                        )
                ))
        );

        ViewsOpenRequest req = ViewsOpenRequest.builder()
                .triggerId(triggerId)
                .view(view)
                .build();

        var resp = methods.viewsOpen(req);
        if (!resp.isOk()) {
            throw new IllegalStateException("Slack modal open failed: " + resp.getError());
        }
    }

    private List<LayoutBlock> buildReviewBlocks(String prTitle, int prNumber, String repo, String summary,
                                                 List<Risk> risks, String planUrl, String buttonValue) {
        var blocksBuilder = new java.util.ArrayList<LayoutBlock>();

        blocksBuilder.add(section(s -> s.text(markdownText(
                "*PR #" + prNumber + ": " + prTitle + "*  (`" + repo + "`)\n" + summary))));

        if (risks.isEmpty()) {
            blocksBuilder.add(section(s -> s.text(markdownText("✅ No risk flags detected"))));
        } else {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (Risk r : risks) {
                if (shown >= 5) {
                    sb.append("\n_+ ").append(risks.size() - shown).append(" more — see full plan_");
                    break;
                }
                sb.append(r.emoji()).append(" ").append(r.getReason())
                        .append(" (`").append(r.getResourceAddress()).append("`)\n");
                shown++;
            }
            blocksBuilder.add(section(s -> s.text(markdownText(sb.toString()))));
        }

        blocksBuilder.add(actions(a -> a.elements(List.of(
                button(b -> b.text(plainText("Approve")).style("primary")
                        .actionId("approve_pr").value(buttonValue)),
                button(b -> b.text(plainText("Reject")).style("danger")
                        .actionId("reject_pr").value(buttonValue)),
                button(b -> b.text(plainText("View Full Plan")).actionId("view_plan").url(planUrl))
        ))));

        return blocksBuilder;
    }
}
