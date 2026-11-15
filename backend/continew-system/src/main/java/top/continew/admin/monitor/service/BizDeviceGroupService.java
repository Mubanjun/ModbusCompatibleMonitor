/*
 * Copyright (c) 2022-present Charles7c Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package top.continew.admin.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.continew.admin.monitor.mapper.BizDeviceGroupMapper;
import top.continew.admin.monitor.mapper.BizDeviceMapper;
import top.continew.admin.monitor.model.entity.BizDeviceDO;
import top.continew.admin.monitor.model.entity.BizDeviceGroupDO;
import top.continew.admin.monitor.model.req.BizDeviceGroupReq;
import top.continew.admin.monitor.model.resp.BizDeviceGroupNode;
import top.continew.starter.core.util.validation.CheckUtils;

/** 文件夹式设备分组树。 */
@Service
@RequiredArgsConstructor
public class BizDeviceGroupService {

    private static final long ROOT_ID = 1L;
    private static final int MAX_DEPTH = 5;
    private final BizDeviceGroupMapper groupMapper;
    private final BizDeviceMapper deviceMapper;

    /** 返回带直接/含子分组计数的完整树。 */
    public List<BizDeviceGroupNode> tree() {
        List<BizDeviceGroupDO> groups =
            groupMapper.selectList(new LambdaQueryWrapper<BizDeviceGroupDO>()
                .eq(BizDeviceGroupDO::getDeleted, 0)
                .orderByAsc(BizDeviceGroupDO::getSortNo, BizDeviceGroupDO::getId));
        Map<Long, Integer> direct = directCounts();
        Map<Long, BizDeviceGroupNode> nodes = new LinkedHashMap<>();
        for (BizDeviceGroupDO g : groups) {
            BizDeviceGroupNode n = toNode(g, direct.getOrDefault(g.getId(), 0));
            nodes.put(g.getId(), n);
        }
        List<BizDeviceGroupNode> roots = new ArrayList<>();
        for (BizDeviceGroupDO g : groups) {
            BizDeviceGroupNode n = nodes.get(g.getId());
            BizDeviceGroupNode parent = nodes.get(g.getParentId());
            if (parent == null) {
                roots.add(n);
            } else {
                parent.getChildren().add(n);
            }
        }
        for (BizDeviceGroupNode root : roots) {
            computeSubtree(root);
        }
        return roots;
    }

    /** 新建分组。 */
    @Transactional
    public Long create(BizDeviceGroupReq req) {
        long parentId = req.getParentId() == null ? ROOT_ID : req.getParentId();
        BizDeviceGroupDO parent = requireGroup(parentId);
        int depth = parent.getDepth() + 1;
        CheckUtils.throwIf(depth > MAX_DEPTH, "分组层级不能超过 {} 层", MAX_DEPTH);
        checkDuplicate(parentId, req.getName(), null);
        BizDeviceGroupDO group = new BizDeviceGroupDO();
        group.setParentId(parentId);
        group.setName(req.getName().trim());
        group.setPath("");
        group.setDepth(depth);
        group.setSortNo(req.getSortNo() == null ? 999 : req.getSortNo());
        group.setDeviceCount(0);
        group.setIsBuiltin(false);
        group.setRemark(req.getRemark());
        groupMapper.insert(group);
        group.setPath(parent.getPath() + group.getId() + "/");
        groupMapper.updateById(group);
        recalcCounts();
        return group.getId();
    }

    /** 修改名称或移动分组。 */
    @Transactional
    public void update(Long id, BizDeviceGroupReq req) {
        BizDeviceGroupDO old = requireGroup(id);
        CheckUtils.throwIf(Boolean.TRUE.equals(old.getIsBuiltin()), "内置根分组不可修改");
        long parentId = req.getParentId() == null ? old.getParentId() : req.getParentId();
        BizDeviceGroupDO parent = requireGroup(parentId);
        CheckUtils.throwIf(parent.getPath().startsWith(old.getPath()), "不能把分组移动到自身或子孙节点");
        int depth = parent.getDepth() + 1;
        CheckUtils.throwIf(depth > MAX_DEPTH, "分组层级不能超过 {} 层", MAX_DEPTH);
        checkDuplicate(parentId, req.getName(), id);
        String oldPath = old.getPath();
        String newPath = parent.getPath() + id + "/";
        old.setName(req.getName().trim());
        old.setParentId(parentId);
        old.setSortNo(req.getSortNo() == null ? old.getSortNo() : req.getSortNo());
        old.setRemark(req.getRemark());
        old.setDepth(depth);
        old.setPath(newPath);
        groupMapper.updateById(old);
        List<BizDeviceGroupDO> all = allGroups();
        for (BizDeviceGroupDO g : all) {
            if (!g.getId().equals(id) && g.getPath() != null && g.getPath().startsWith(oldPath)) {
                g.setPath(newPath + g.getPath().substring(oldPath.length()));
                g.setDepth(g.getDepth() + depth - old.getDepth());
                groupMapper.updateById(g);
            }
        }
        recalcCounts();
    }

    /** 删除分组并按策略处理子分组与设备。 */
    @Transactional
    public void delete(Long id, String strategy) {
        BizDeviceGroupDO target = requireGroup(id);
        CheckUtils.throwIf(Boolean.TRUE.equals(target.getIsBuiltin()), "内置根分组不可删除");
        List<BizDeviceGroupDO> children =
            allGroups().stream().filter(g -> id.equals(g.getParentId())).toList();
        long devices = deviceMapper
            .selectCount(new LambdaQueryWrapper<BizDeviceDO>().eq(BizDeviceDO::getGroupId, id));
        if (!children.isEmpty() || devices > 0) {
            CheckUtils.throwIf("REJECT".equalsIgnoreCase(strategy), "分组下存在子分组或设备，不能直接删除");
            BizDeviceGroupDO parent = requireGroup(target.getParentId());
            for (BizDeviceGroupDO child : children) {
                child.setParentId(parent.getId());
                child.setPath(parent.getPath() + child.getId() + "/");
                child.setDepth(parent.getDepth() + 1);
                groupMapper.updateById(child);
            }
            deviceMapper.update(null, new LambdaUpdateWrapper<BizDeviceDO>()
                .eq(BizDeviceDO::getGroupId, id)
                .set(BizDeviceDO::getGroupId, parent.getId()));
        }
        groupMapper.deleteById(id);
        recalcCounts();
    }

    /** 批量移动设备到指定分组。 */
    @Transactional
    public void moveDevices(List<Long> deviceIds, Long groupId) {
        requireGroup(groupId);
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        deviceMapper.update(null, new LambdaUpdateWrapper<BizDeviceDO>()
            .in(BizDeviceDO::getId, deviceIds)
            .set(BizDeviceDO::getGroupId, groupId));
        recalcCounts();
    }

    /** 返回分组自身及子孙 ID，供列表/权限过滤使用。 */
    public List<Long> descendantIds(Long groupId, boolean includeSelf) {
        BizDeviceGroupDO group = requireGroup(groupId);
        List<Long> ids = allGroups().stream()
            .filter(g -> includeSelf ? g.getId().equals(groupId) : !g.getId().equals(groupId))
            .filter(g -> g.getPath().startsWith(group.getPath()))
            .map(BizDeviceGroupDO::getId)
            .toList();
        return new ArrayList<>(ids);
    }

    private BizDeviceGroupDO requireGroup(Long id) {
        BizDeviceGroupDO group = id == null ? null : groupMapper.selectById(id);
        CheckUtils.throwIf(
            group == null || (group.getDeleted() != null && group.getDeleted() != 0L), "分组不存在");
        return group;
    }

    private List<BizDeviceGroupDO> allGroups() {
        return groupMapper.selectList(new LambdaQueryWrapper<BizDeviceGroupDO>()
            .eq(BizDeviceGroupDO::getDeleted, 0)
            .orderByAsc(BizDeviceGroupDO::getSortNo, BizDeviceGroupDO::getId));
    }

    private void checkDuplicate(Long parentId, String name, Long excludeId) {
        long count = groupMapper.selectCount(new LambdaQueryWrapper<BizDeviceGroupDO>()
            .eq(BizDeviceGroupDO::getParentId, parentId)
            .eq(BizDeviceGroupDO::getName, name == null ? "" : name.trim())
            .eq(BizDeviceGroupDO::getDeleted, 0)
            .ne(excludeId != null, BizDeviceGroupDO::getId, excludeId));
        CheckUtils.throwIf(count > 0, "同一父分组下已存在同名分组");
    }

    private Map<Long, Integer> directCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (BizDeviceDO d : deviceMapper.selectList(new LambdaQueryWrapper<BizDeviceDO>()
            .eq(BizDeviceDO::getDeleted, 0)
            .select(BizDeviceDO::getGroupId))) {
            if (d.getGroupId() != null) {
                counts.merge(d.getGroupId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private BizDeviceGroupNode toNode(BizDeviceGroupDO g, int direct) {
        BizDeviceGroupNode n = new BizDeviceGroupNode();
        n.setId(g.getId());
        n.setParentId(g.getParentId());
        n.setName(g.getName());
        n.setPath(g.getPath());
        n.setDepth(g.getDepth());
        n.setSortNo(g.getSortNo());
        n.setDirectDeviceCount(direct);
        n.setSubtreeDeviceCount(direct);
        n.setBuiltin(g.getIsBuiltin());
        n.setRemark(g.getRemark());
        n.getChildren().sort(Comparator.comparing(BizDeviceGroupNode::getSortNo)
            .thenComparing(BizDeviceGroupNode::getId));
        return n;
    }

    private int computeSubtree(BizDeviceGroupNode node) {
        int total = node.getDirectDeviceCount() == null ? 0 : node.getDirectDeviceCount();
        for (BizDeviceGroupNode child : node.getChildren()) {
            total += computeSubtree(child);
        }
        node.setSubtreeDeviceCount(total);
        node.getChildren().sort(Comparator.comparing(BizDeviceGroupNode::getSortNo)
            .thenComparing(BizDeviceGroupNode::getId));
        return total;
    }

    private void recalcCounts() {
        Map<Long, Integer> counts = directCounts();
        for (BizDeviceGroupDO g : allGroups()) {
            int c = counts.getOrDefault(g.getId(), 0);
            if (!Integer.valueOf(c).equals(g.getDeviceCount())) {
                g.setDeviceCount(c);
                groupMapper.updateById(g);
            }
        }
    }
}
