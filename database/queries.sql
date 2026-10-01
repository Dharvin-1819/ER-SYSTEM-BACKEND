-- CSRM MySQL reference queries. Parameters use named placeholders for documentation.
-- Endpoints implement strict half-open intervals: [start, end).
SELECT * FROM bookings WHERE resource_id = :resourceId AND status='CONFIRMED'
AND start_time < :endTime AND end_time > :startTime AND (:excludeId IS NULL OR id <> :excludeId);

-- Daily utilization clipped to the selected day; denominator is a 24-hour day.
SELECT r.id,r.name,COALESCE(SUM(TIMESTAMPDIFF(MINUTE,
GREATEST(b.start_time,CAST(:reportDate AS DATETIME)),
LEAST(b.end_time,DATE_ADD(CAST(:reportDate AS DATETIME),INTERVAL 1 DAY)))),0) AS booked_minutes
FROM resources r LEFT JOIN bookings b ON b.resource_id=r.id AND b.status='CONFIRMED'
AND b.start_time<DATE_ADD(CAST(:reportDate AS DATETIME),INTERVAL 1 DAY) AND b.end_time>CAST(:reportDate AS DATETIME)
GROUP BY r.id,r.name;

SELECT * FROM audit_logs WHERE (:userId IS NULL OR user_id=:userId)
AND (:auditDate IS NULL OR DATE(timestamp)=:auditDate) ORDER BY timestamp DESC;
