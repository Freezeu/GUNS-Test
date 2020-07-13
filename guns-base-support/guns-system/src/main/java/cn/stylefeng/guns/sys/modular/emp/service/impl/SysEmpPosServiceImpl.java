package cn.stylefeng.guns.sys.modular.emp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.lang.Dict;
import cn.stylefeng.guns.sys.modular.emp.entity.SysEmpPos;
import cn.stylefeng.guns.sys.modular.emp.mapper.SysEmpPosMapper;
import cn.stylefeng.guns.sys.modular.emp.service.SysEmpPosService;
import cn.stylefeng.guns.sys.modular.pos.entity.SysPos;
import cn.stylefeng.guns.sys.modular.pos.service.SysPosService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 员工职位service接口实现类
 *
 * @author xuyuxiang
 * @date 2020/3/13 15:11
 */
@Service
public class SysEmpPosServiceImpl extends ServiceImpl<SysEmpPosMapper, SysEmpPos> implements SysEmpPosService {

    @Resource
    private SysPosService sysPosService;

    /**
     * 职位id参数键
     */
    private static final String POS_ID_DICT_KEY = "posId";

    /**
     * 职位编码参数键
     */
    private static final String POS_CODE_DICT_KEY = "posCode";

    /**
     * 职位名称参数键
     */
    private static final String POS_NAME_DICT_KEY = "posName";

    /**
     * 保存附属机构相关信息
     *
     * @author xuyuxiang
     * @date 2020/4/2 9:01
     */
    @Override
    public void addOrEdit(Long empId, List<Long> posIdList) {
        LambdaQueryWrapper<SysEmpPos> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysEmpPos::getEmpId, empId);

        //删除职位信息
        this.remove(queryWrapper);

        //保存职位信息
        posIdList.forEach(posId -> {
            SysEmpPos sysEmpPos = new SysEmpPos();
            sysEmpPos.setEmpId(empId);
            sysEmpPos.setPosId(Convert.toLong(posId));
            this.save(sysEmpPos);
        });
    }

    /**
     * 获取所属职位信息
     *
     * @author xuyuxiang
     * @date 2020/4/2 20:07
     */
    @Override
    public List<Dict> getEmpPosDictList(Long empId, boolean isFillId) {
        List<Dict> dictList = CollectionUtil.newArrayList();

        LambdaQueryWrapper<SysEmpPos> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysEmpPos::getEmpId, empId);

        this.list(queryWrapper).forEach(sysEmpPos -> {
            Dict dict = Dict.create();
            Long posId = sysEmpPos.getPosId();
            SysPos sysPos = sysPosService.getById(posId);
            if(isFillId) {
                dict.put(POS_ID_DICT_KEY, posId);
            }
            dict.put(POS_CODE_DICT_KEY, sysPos.getCode());
            dict.put(POS_NAME_DICT_KEY, sysPos.getName());
            dictList.add(dict);
        });
        return dictList;
    }

    /**
     * 根据职位id判断该职位下是否有员工
     *
     * @author xuyuxiang
     * @date 2020/6/23 10:41
     */
    @Override
    public boolean hasPosEmp(Long posId) {
        LambdaQueryWrapper<SysEmpPos> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysEmpPos::getPosId, posId);
        List<SysEmpPos> list = this.list(queryWrapper);
        return list.size() != 0;
    }

    /**
     * 根据员工id删除对用的员工-职位信息
     *
     * @author xuyuxiang
     * @date 2020/6/28 14:58
     */
    @Override
    public void deleteEmpPosInfoByUserId(Long empId) {
        LambdaQueryWrapper<SysEmpPos> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysEmpPos::getEmpId, empId);
        this.remove(queryWrapper);
    }
}