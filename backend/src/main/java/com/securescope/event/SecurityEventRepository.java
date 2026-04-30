package com.securescope.event;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    Page<SecurityEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    @Query("SELECT e.sourceIp, COUNT(e) FROM SecurityEvent e GROUP BY e.sourceIp ORDER BY COUNT(e) DESC")
    List<Object[]> countBySourceIp();
}
