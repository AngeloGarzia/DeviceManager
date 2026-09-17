package com.devicemanager.repository;

import com.devicemanager.entity.ScheduledMailSend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledMailSendRepository extends JpaRepository<ScheduledMailSend, Long> {

    boolean existsByJobKeyAndPeriodKeyAndRecipient(String jobKey, String periodKey, String recipient);

    void deleteByJobKeyAndPeriodKeyAndRecipient(String jobKey, String periodKey, String recipient);
}
