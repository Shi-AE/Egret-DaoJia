package com.edj.foundations.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edj.common.domain.PageResult;
import com.edj.common.expcetions.BadRequestException;
import com.edj.common.utils.AsyncUtils;
import com.edj.common.utils.BeanUtils;
import com.edj.common.utils.EnumUtils;
import com.edj.common.utils.ObjectUtils;
import com.edj.foundations.domain.dto.RegionAddDTO;
import com.edj.foundations.domain.dto.RegionPageDTO;
import com.edj.foundations.domain.dto.RegionUpdateDTO;
import com.edj.foundations.domain.entity.EdjCity;
import com.edj.foundations.domain.entity.EdjRegion;
import com.edj.foundations.domain.vo.RegionVO;
import com.edj.foundations.enums.EdjRegionActiveStatus;
import com.edj.foundations.mapper.EdjCityMapper;
import com.edj.foundations.mapper.EdjRegionMapper;
import com.edj.foundations.service.EdjConfigRegionService;
import com.edj.foundations.service.EdjRegionService;
import com.edj.mysql.utils.PageUtils;
import com.github.yulichang.base.MPJBaseServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 针对表【edj_region(区域表)】的数据库操作Service实现
 *
 * @author A.E.
 * @date 2024/10/16
 */
@Service
@RequiredArgsConstructor
public class EdjRegionServiceImpl extends MPJBaseServiceImpl<EdjRegionMapper, EdjRegion> implements EdjRegionService {

    private final EdjCityMapper cityMapper;

    private final EdjConfigRegionService configRegionService;

    @Override
    @Transactional
    public void add(RegionAddDTO regionAddDTO) {
        // 检查城市重复
        Integer edjCityId = regionAddDTO.getEdjCityId();
        LambdaQueryWrapper<EdjRegion> checkWrapper = new LambdaQueryWrapper<EdjRegion>()
                .select(EdjRegion::getId)
                .eq(EdjRegion::getEdjCityId, edjCityId);
        boolean exists = baseMapper.exists(checkWrapper);
        if (exists) {
            throw new BadRequestException("提交重复城市");
        }

        // 检查城市
        LambdaQueryWrapper<EdjCity> cityLambdaQueryWrapper = new LambdaQueryWrapper<EdjCity>()
                .select(EdjCity::getSortNum)
                .eq(EdjCity::getId, edjCityId);
        EdjCity city = cityMapper.selectOne(cityLambdaQueryWrapper);
        // 检查存在
        if (ObjectUtils.isNull(city)) {
            throw new BadRequestException("城市不存在");
        }
        // 获取城市排序位
        Integer sortNum = city.getSortNum();

        // 新增区域
        EdjRegion region = BeanUtils.toBean(regionAddDTO, EdjRegion.class);
        region.setSortNum(sortNum);
        baseMapper.insert(region);

        // 初始化区域配置
        Long id = region.getId();
        configRegionService.init(id, edjCityId);
    }

    @Override
    @Transactional
    public void update(RegionUpdateDTO regionUpdateDTO) {
        EdjRegion region = BeanUtils.toBean(regionUpdateDTO, EdjRegion.class);
        baseMapper.updateById(region);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        // 检查区域
        LambdaQueryWrapper<EdjRegion> wrapper = new LambdaQueryWrapper<EdjRegion>()
                .select(EdjRegion::getActiveStatus)
                .eq(EdjRegion::getId, id);
        EdjRegion region = baseMapper.selectOne(wrapper);

        // 检查不存在
        if (ObjectUtils.isNull(region)) {
            throw new BadRequestException("区域不存在");
        }
        // 检查状态
        Integer activeStatus = region.getActiveStatus();
        if (EnumUtils.notEquals(EdjRegionActiveStatus.DRAFTS, activeStatus)) {
            throw new BadRequestException("草稿状态方可删除");
        }

        baseMapper.deleteById(id);

        // 异步移除区域配置
        AsyncUtils.runAsyncComplete(() -> configRegionService.delete(id));
    }

    @Override
    public PageResult<RegionVO> page(RegionPageDTO regionPageDTO) {
        Page<EdjRegion> page = PageUtils.parsePageQuery(regionPageDTO);
        Page<EdjRegion> serveTypePage = baseMapper.selectPage(page, null);
        return PageUtils.toPage(serveTypePage, RegionVO.class);
    }
}