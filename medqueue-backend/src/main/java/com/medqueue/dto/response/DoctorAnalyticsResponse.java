package com.medqueue.dto.response;
public record DoctorAnalyticsResponse(Long doctorId, String doctorName, long waiting, long served,
        long averageWaitMinutes, long noShows, double noShowRate, boolean available, boolean queueOpen) { }
