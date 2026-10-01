package com.medqueue.dto.response;
import com.medqueue.entity.Department;
public record DepartmentResponse(Long id, String name) { public static DepartmentResponse from(Department d) { return new DepartmentResponse(d.getId(), d.getName()); } }
