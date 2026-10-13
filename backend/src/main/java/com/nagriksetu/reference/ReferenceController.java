package com.nagriksetu.reference;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference")
public class ReferenceController {

    public record CategoryDto(Long id, String code, String name, String description, String defaultDepartment) {
    }

    public record DepartmentDto(Long id, String code, String name, String description) {
    }

    public record WardDto(Long id, String code, String name, String city, boolean hasBoundary,
                          String boundaryJson, String boundarySource) {
    }

    private final CategoryRepository categories;
    private final DepartmentRepository departments;
    private final WardRepository wards;

    public ReferenceController(CategoryRepository categories, DepartmentRepository departments, WardRepository wards) {
        this.categories = categories;
        this.departments = departments;
        this.wards = wards;
    }

    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public List<CategoryDto> categories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription(),
                        c.getDefaultDepartment() == null ? null : c.getDefaultDepartment().getName()))
                .toList();
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return departments.findAllByOrderByNameAsc().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getCode(), d.getName(), d.getDescription()))
                .toList();
    }

    @GetMapping("/wards")
    public List<WardDto> wards() {
        return wards.findAllByOrderByNameAsc().stream()
                .map(w -> new WardDto(w.getId(), w.getCode(), w.getName(), w.getCity(), w.getBoundaryJson() != null,
                        w.getBoundaryJson(), w.getBoundarySource()))
                .toList();
    }
}
