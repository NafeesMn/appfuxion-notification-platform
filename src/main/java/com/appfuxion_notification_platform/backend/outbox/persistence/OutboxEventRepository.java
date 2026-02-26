package com.appfuxion_notification_platform.backend.outbox.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.appfuxion_notification_platform.backend.outbox.domain.OutboxEventStatus;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("""
        select e
        from OutboxEvent e
        where e.status in :statuses
          and e.availableAt <= :cutoff
        order by e.availableAt asc, e.createdAt asc
        """)
    List<OutboxEvent> findReadyForDispatch(
            @Param("statuses") Collection<OutboxEventStatus> statuses,
            @Param("cutoff") Instant cutoff,
            Pageable pageable);
}
