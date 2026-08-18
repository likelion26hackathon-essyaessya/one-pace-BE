package io.github.essyaessya.onepace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "action_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_summary_log_id")
    private MeetingSummaryLog meetingSummaryLog;

    @Column(name = "title")
    private String title;

    @Column(name = "assignee", length = 100)
    private String assignee;

    @Column(name = "due_date", length = 50)
    private String dueDate;

    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "OPEN";
}
