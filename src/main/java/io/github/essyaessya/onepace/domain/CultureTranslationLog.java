package io.github.essyaessya.onepace.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "culture_translation_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CultureTranslationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "counterpart_country", length = 2)
    private String counterpartCountry;

    @Column(name = "risk_detected")
    private boolean riskDetected;

    @Column(name = "detected_expression", columnDefinition = "TEXT")
    private String detectedExpression;

    @Column(name = "realtime_detection", columnDefinition = "TEXT")
    private String realtimeDetection;

    @Column(name = "nuance_explanation", columnDefinition = "TEXT")
    private String nuanceExplanation;

    @Column(name = "suggested_text", columnDefinition = "TEXT")
    private String suggestedText;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
