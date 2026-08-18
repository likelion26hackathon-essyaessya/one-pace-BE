package io.github.essyaessya.onepace.repository;

import io.github.essyaessya.onepace.domain.MeetingSummaryLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetingSummaryLogRepository extends JpaRepository<MeetingSummaryLog, Long> {

    List<MeetingSummaryLog> findTop20ByOrderByCreatedAtDesc();
}
