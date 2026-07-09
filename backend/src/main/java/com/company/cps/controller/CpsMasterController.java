package com.company.cps.controller;

import com.company.cps.dto.CpsOptionResponse;
import com.company.cps.service.CpsMasterDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cps/master")
public class CpsMasterController {

    private final CpsMasterDataService masterDataService;

    public CpsMasterController(CpsMasterDataService masterDataService) {
        this.masterDataService = masterDataService;
    }

    /**
     * 查询启用的工厂选项，供移动端位置选择使用。
     */
    @GetMapping("/factories")
    public List<CpsOptionResponse> factories() {
        return masterDataService.getFactories();
    }

    /**
     * 根据工厂查询启用的区域选项，供移动端位置选择使用。
     */
    @GetMapping("/areas")
    public List<CpsOptionResponse> areas(@RequestParam String factory) {
        return masterDataService.getAreas(factory);
    }

    /**
     * 根据区域查询启用的拉线选项，供移动端位置选择使用。
     */
    @GetMapping("/lines")
    public List<CpsOptionResponse> lines(@RequestParam String factory, @RequestParam String area) {
        return masterDataService.getLines(factory, area);
    }

    /**
     * 根据区域和可选拉线查询启用的工序选项，供移动端位置选择使用。
     */
    @GetMapping("/processes")
    public List<CpsOptionResponse> processes(
            @RequestParam String factory,
            @RequestParam String area,
            @RequestParam(required = false) String line
    ) {
        return masterDataService.getProcesses(factory, area, line);
    }

    /**
     * 查询启用的问题分类选项；parentId 为空时返回一级分类，传入一级分类ID时返回二级分类。
     */
    @GetMapping("/categories")
    public List<CpsOptionResponse> categories(@RequestParam(required = false) Long parentId) {
        return masterDataService.getCategoryOptions(parentId);
    }
}
