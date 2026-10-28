package com.catlbattery.app.service.cps;

import com.catlbattery.domain.entity.cps.CpsDict;
import com.catlbattery.domain.entity.cps.CpsDictType;
import com.catlbattery.domain.entity.cps.dto.CpsDictRequest;
import com.catlbattery.domain.entity.cps.dto.CpsDictTypeRequest;
import com.catlbattery.infra.mapper.cps.CpsDictMapper;
import com.catlbattery.infra.mapper.cps.CpsDictTypeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 通用字典（双表）：条目类型解析/唯一性/内置保护 + 类型 CRUD（编码规则/唯一/内置保护/删除护栏）。 */
@ExtendWith(MockitoExtension.class)
class CpsDictServiceTest {

    @Mock private CpsDictMapper dictMapper;
    @Mock private CpsDictTypeMapper typeMapper;

    private CpsDictService service;
    private CpsDictRequest request;

    @BeforeEach
    void setUp() {
        service = new CpsDictService(dictMapper, typeMapper);
        request = new CpsDictRequest();
        request.setDictType("ROOM_TYPE");
        request.setItemValue("配电房");
        request.setItemLabel("配电房");
    }

    @Test
    void saveValidatesRequiredFieldsBeforeTypeLookup() {
        request.setDictType("");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setDictType("room_type");
        // 小写编码：类型表查无此码 → IAE（类型不存在）
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setDictType("ROOM_TYPE");
        request.setItemValue(" ");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        request.setItemValue("配电房");
        request.setItemLabel("");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void saveInsertsNewItemWithDefaultsAndResolvedTypeId() {
        when(typeMapper.findByCode("ROOM_TYPE")).thenReturn(Optional.of(roomType()));
        when(dictMapper.findByTypeAndValue("ROOM_TYPE", "配电房")).thenReturn(Optional.empty());
        final CpsDict[] inserted = new CpsDict[1];
        when(dictMapper.insert(any(CpsDict.class))).thenAnswer(inv -> {
            inserted[0] = (CpsDict) inv.getArgument(0);
            inserted[0].setId(1L);
            return 1;
        });
        when(dictMapper.findById(1L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.save(null, request, "admin");

        verify(dictMapper).insert(any(CpsDict.class));
        assertEquals(Long.valueOf(5L), inserted[0].getTypeId());
        assertEquals(Boolean.TRUE, inserted[0].getEnabled());
        assertEquals(Boolean.FALSE, inserted[0].getBuiltin());
        assertEquals(Integer.valueOf(0), inserted[0].getSortNo());
    }

    @Test
    void saveRejectsUnknownTypeAndDisabledType() {
        when(typeMapper.findByCode("NOT_EXISTS")).thenReturn(Optional.empty());
        request.setDictType("NOT_EXISTS");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));

        CpsDictType disabled = roomType();
        disabled.setEnabled(false);
        when(typeMapper.findByCode("ROOM_TYPE")).thenReturn(Optional.of(disabled));
        request.setDictType("ROOM_TYPE");
        assertThrows(IllegalArgumentException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void saveRejectsDuplicateTypeAndValue() {
        when(typeMapper.findByCode("ROOM_TYPE")).thenReturn(Optional.of(roomType()));
        CpsDict existing = new CpsDict();
        existing.setId(9L);
        when(dictMapper.findByTypeAndValue("ROOM_TYPE", "配电房")).thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class, () -> service.save(null, request, "admin"));
    }

    @Test
    void builtinItemCannotChangeTypeOrValueOrDisable() {
        CpsDict existing = builtinItem();
        when(typeMapper.findByCode("RISK_LEVEL")).thenReturn(Optional.of(riskType()));
        when(typeMapper.findByCode("OTHER_TYPE")).thenReturn(Optional.of(otherType()));
        when(dictMapper.findById(1L)).thenReturn(Optional.of(existing));

        request.setDictType("RISK_LEVEL");
        request.setItemValue("EXTREME");
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request, "admin"));

        request.setItemValue("HIGH");
        request.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request, "admin"));

        request.setEnabled(true);
        request.setDictType("OTHER_TYPE");
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, request, "admin"));
    }

    @Test
    void builtinItemCanRelabelAndResort() {
        CpsDict existing = builtinItem();
        when(typeMapper.findByCode("RISK_LEVEL")).thenReturn(Optional.of(riskType()));
        when(dictMapper.findById(1L)).thenReturn(Optional.of(existing));
        when(dictMapper.findByTypeAndValue("RISK_LEVEL", "HIGH")).thenReturn(Optional.of(existing));
        when(dictMapper.update(any(CpsDict.class))).thenReturn(1);

        request.setDictType("RISK_LEVEL");
        request.setItemValue("HIGH");
        request.setItemLabel("高风险（新版）");
        request.setSortNo(9);
        service.save(1L, request, "admin");

        verify(dictMapper).update(any(CpsDict.class));
        verify(dictMapper, never()).insert(any(CpsDict.class));
    }

    @Test
    void setEnabledRejectsDisablingBuiltinButAllowsNormal() {
        when(dictMapper.findById(1L)).thenReturn(Optional.of(builtinItem()));
        assertThrows(IllegalArgumentException.class, () -> service.setEnabled(1L, false, "admin"));

        CpsDict normal = new CpsDict();
        normal.setId(2L);
        normal.setBuiltin(false);
        when(dictMapper.findById(2L)).thenReturn(Optional.of(normal));
        when(dictMapper.setEnabled(2L, false, "admin")).thenReturn(1);
        service.setEnabled(2L, false, "admin");
        verify(dictMapper).setEnabled(2L, false, "admin");
    }

    @Test
    void deleteRejectsBuiltinAndDeletesNormal() {
        when(dictMapper.findById(1L)).thenReturn(Optional.of(builtinItem()));
        assertThrows(IllegalArgumentException.class, () -> service.delete(1L));

        CpsDict normal = new CpsDict();
        normal.setId(2L);
        normal.setBuiltin(false);
        when(dictMapper.findById(2L)).thenReturn(Optional.of(normal));
        when(dictMapper.deleteById(2L)).thenReturn(1);
        service.delete(2L);
        verify(dictMapper).deleteById(2L);
    }

    @Test
    void valueExistsBlankReturnsFalseWithoutDbAndDelegatesCount() {
        assertFalse(service.valueExists("ROOM_TYPE", null));
        assertFalse(service.valueExists("ROOM_TYPE", " "));
        assertFalse(service.valueExists(null, "配电房"));
        verify(dictMapper, never()).countEnabled(anyString(), anyString());

        when(dictMapper.countEnabled("ROOM_TYPE", "配电房")).thenReturn(2);
        assertTrue(service.valueExists("ROOM_TYPE", "配电房"));

        when(dictMapper.countEnabled("ROOM_TYPE", "未知房型")).thenReturn(0);
        assertFalse(service.valueExists("ROOM_TYPE", "未知房型"));
    }

    // ---------- 字典类型 ----------

    @Test
    void saveTypeValidatesCodePatternAndUniqueness() {
        CpsDictTypeRequest typeRequest = new CpsDictTypeRequest();
        typeRequest.setTypeName("班组");
        assertThrows(IllegalArgumentException.class, () -> service.saveType(null, typeRequest, "admin"));

        typeRequest.setTypeCode("shift");
        assertThrows(IllegalArgumentException.class, () -> service.saveType(null, typeRequest, "admin"));

        typeRequest.setTypeCode("SHIFT");
        typeRequest.setTypeName("");
        assertThrows(IllegalArgumentException.class, () -> service.saveType(null, typeRequest, "admin"));

        typeRequest.setTypeName("班组");
        when(typeMapper.findByCode("SHIFT")).thenReturn(Optional.of(otherType()));
        assertThrows(IllegalStateException.class, () -> service.saveType(null, typeRequest, "admin"));
    }

    @Test
    void saveTypeInsertsWithDefaultsAndBuiltinFalse() {
        CpsDictTypeRequest typeRequest = new CpsDictTypeRequest();
        typeRequest.setTypeCode("SHIFT");
        typeRequest.setTypeName("班组");
        when(typeMapper.findByCode("SHIFT")).thenReturn(Optional.empty());
        final CpsDictType[] inserted = new CpsDictType[1];
        when(typeMapper.insert(any(CpsDictType.class))).thenAnswer(inv -> {
            inserted[0] = (CpsDictType) inv.getArgument(0);
            inserted[0].setId(9L);
            return 1;
        });
        when(typeMapper.findById(9L)).thenAnswer(inv -> Optional.of(inserted[0]));

        service.saveType(null, typeRequest, "admin");

        assertEquals(Boolean.TRUE, inserted[0].getEnabled());
        assertEquals(Boolean.FALSE, inserted[0].getBuiltin());
        assertEquals(Integer.valueOf(0), inserted[0].getSortNo());
    }

    @Test
    void builtinTypeCannotChangeCodeOrDisableButCanRelabel() {
        CpsDictTypeRequest typeRequest = new CpsDictTypeRequest();
        when(typeMapper.findById(2L)).thenReturn(Optional.of(riskType()));

        typeRequest.setTypeName("风险等级");
        typeRequest.setTypeCode("RISK");
        assertThrows(IllegalArgumentException.class, () -> service.saveType(2L, typeRequest, "admin"));

        typeRequest.setTypeCode("RISK_LEVEL");
        typeRequest.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.saveType(2L, typeRequest, "admin"));

        typeRequest.setEnabled(true);
        typeRequest.setDescription("改名/说明/排序允许");
        when(typeMapper.update(any(CpsDictType.class))).thenReturn(1);
        service.saveType(2L, typeRequest, "admin");
        verify(typeMapper).update(any(CpsDictType.class));
        verify(typeMapper, never()).insert(any(CpsDictType.class));
    }

    @Test
    void setTypeEnabledRejectsBuiltinButAllowsNormal() {
        when(typeMapper.findById(2L)).thenReturn(Optional.of(riskType()));
        assertThrows(IllegalArgumentException.class, () -> service.setTypeEnabled(2L, false, "admin"));

        when(typeMapper.findById(9L)).thenReturn(Optional.of(otherType()));
        when(typeMapper.setEnabled(9L, false, "admin")).thenReturn(1);
        service.setTypeEnabled(9L, false, "admin");
        verify(typeMapper).setEnabled(9L, false, "admin");
    }

    @Test
    void deleteTypeRejectsBuiltinAndRejectsWhenItemsRemain() {
        when(typeMapper.findById(2L)).thenReturn(Optional.of(riskType()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteType(2L));

        when(typeMapper.findById(9L)).thenReturn(Optional.of(otherType()));
        when(dictMapper.countByTypeId(9L)).thenReturn(3);
        assertThrows(IllegalStateException.class, () -> service.deleteType(9L));

        when(dictMapper.countByTypeId(9L)).thenReturn(0);
        when(typeMapper.deleteById(9L)).thenReturn(1);
        service.deleteType(9L);
        verify(typeMapper).deleteById(9L);
    }

    private static CpsDictType roomType() {
        return typeOf(5L, "ROOM_TYPE", "房型");
    }

    private static CpsDictType riskType() {
        CpsDictType type = typeOf(2L, "RISK_LEVEL", "风险等级");
        type.setBuiltin(true);
        return type;
    }

    private static CpsDictType otherType() {
        return typeOf(9L, "OTHER_TYPE", "其他");
    }

    private static CpsDictType typeOf(Long id, String code, String name) {
        CpsDictType type = new CpsDictType();
        type.setId(id);
        type.setTypeCode(code);
        type.setTypeName(name);
        type.setBuiltin(false);
        type.setEnabled(true);
        return type;
    }

    private static CpsDict builtinItem() {
        CpsDict item = new CpsDict();
        item.setId(1L);
        item.setTypeId(2L);
        item.setTypeCode("RISK_LEVEL");
        item.setItemValue("HIGH");
        item.setItemLabel("高风险");
        item.setBuiltin(true);
        item.setEnabled(true);
        return item;
    }
}
