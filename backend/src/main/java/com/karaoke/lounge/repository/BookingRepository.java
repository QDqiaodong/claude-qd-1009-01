package com.karaoke.lounge.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.karaoke.lounge.entity.Booking;

import jakarta.persistence.LockModeType;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByRoom_Id(Long roomId);

    List<Booking> findByMemberId(Long memberId);

    List<Booking> findByMemberIdAndStatus(Long memberId, String status);

    /** 结账时锁住消费单，避免重复结账与并发状态覆盖 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findWithPessimisticLockById(@Param("id") Long id);

    List<Booking> findByStatus(String status);

    List<Booking> findByRoom_IdAndStatusNot(Long roomId, String status);

    List<Booking> findByRoom_IdAndStatus(Long roomId, String status);

    /** 该包厢最近一场未取消的预订（结束使用生成清场任务时关联场次用） */
    Booking findTopByRoom_IdAndStatusNotOrderByEndAtDesc(Long roomId, String status);
}
