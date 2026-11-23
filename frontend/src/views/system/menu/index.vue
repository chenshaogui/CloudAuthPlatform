<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="queryParams.menuName" placeholder="请输入菜单名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="状态" prop="status">
              <el-select v-model="queryParams.status" placeholder="菜单状态" clearable>
                <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </transition>

    <el-card shadow="hover">
      <template #header>
        <el-row :gutter="10">
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:menu:add']" type="primary" plain icon="Plus" @click="handleAdd()">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:menu:remove']" type="danger" plain icon="Delete" @click="handleCascadeDelete" :loading="deleteLoading"
              >级联删除</el-button
            >
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:menu:upload']" type="success" plain icon="Upload" @click="handleImport">导入</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:menu:download']" type="info" plain icon="Download" @click="handleExport()">导出</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table
        ref="menuTableRef"
        v-loading="loading"
        :data="menuList"
        row-key="menuId"
        border
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        :default-expand-all="false"
        lazy
        :load="getChildrenList"
        :expand-change="expandMenuHandle"
      >
        <el-table-column prop="menuName" label="菜单名称" :show-overflow-tooltip="true" width="160"></el-table-column>
        <el-table-column prop="icon" label="图标" align="center" width="100">
          <template #default="scope">
            <svg-icon :icon-class="scope.row.icon" />
          </template>
        </el-table-column>
        <el-table-column prop="orderNum" label="排序" width="60"></el-table-column>
        <el-table-column prop="perms" label="权限标识" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="component" label="组件路径" :show-overflow-tooltip="true"></el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="scope">
            <dict-tag :options="sys_normal_disable" :value="scope.row.status" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime">
          <template #default="scope">
            <span>{{ scope.row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column fixed="right" label="操作" width="180">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['system:menu:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" />
            </el-tooltip>
            <el-tooltip content="新增" placement="top">
              <el-button v-hasPermi="['system:menu:add']" link type="primary" icon="Plus" @click="handleAdd(scope.row)" />
            </el-tooltip>
            <el-tooltip content="导入" placement="top" v-if="scope.row.parentId === 0">
              <el-button v-hasPermi="['system:menu:upload']" link type="success" icon="Upload" @click="handleUploadById(scope.row)" />
            </el-tooltip>
            <el-tooltip content="导出" placement="top" v-if="scope.row.parentId === 0">
              <el-button v-hasPermi="['system:menu:download']" link type="info" icon="Download" @click="handleExportById(scope.row)" />
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['system:menu:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)" />
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialog.visible" :title="dialog.title" destroy-on-close append-to-bod width="750px">
      <el-form ref="menuFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="上级菜单">
              <el-tree-select
                v-model="form.parentId"
                :data="menuOptions"
                :props="{ value: 'menuId', label: 'menuName', children: 'children' } as any"
                value-key="menuId"
                placeholder="选择上级菜单"
                check-strictly
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group v-model="form.menuType">
                <el-radio value="A">应用</el-radio>
                <el-radio value="M">目录</el-radio>
                <el-radio value="C">菜单</el-radio>
                <el-radio value="F">按钮</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="24">
            <el-form-item label="图标类型" prop="menuType" v-if="(form.menuType === 'M' && form.isFrame === '0') || form.menuType === 'A'">
              <el-radio-group v-model="iconType">
                <el-radio value="ICON">图标</el-radio>
                <el-radio value="UPLOAD">上传图片</el-radio>
              </el-radio-group>
            </el-form-item>
            <template v-if="iconType === 'ICON'">
              <el-form-item label="菜单图标" prop="icon">
                <!-- 图标选择器 -->
                <icon-select v-model="form.icon" />
              </el-form-item>
            </template>
            <template v-else-if="iconType === 'UPLOAD'">
              <el-form-item label="菜单图标" prop="icon">
                <imageUpload v-model="form.icon" :limit="1" value-type="url" />
              </el-form-item>
            </template>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input v-model="form.menuName" placeholder="请输入菜单名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="orderNum">
              <el-input-number v-model="form.orderNum" controls-position="right" :min="0" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item>
              <template #label>
                <span>
                  <el-tooltip content="选择是外链则路由地址需要以`http(s)://`开头" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon> </el-tooltip
                  >是否外链
                </span>
              </template>
              <el-radio-group v-model="form.isFrame">
                <el-radio value="0">是</el-radio>
                <el-radio value="1">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item prop="path">
              <template #label>
                <span>
                  <el-tooltip content="访问的路由地址，如：`user`，如外网地址需内链访问则以`http(s)://`开头" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  路由地址
                </span>
              </template>
              <el-input v-model="form.path" placeholder="请输入路由地址" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item prop="component">
              <template #label>
                <span>
                  <el-tooltip content="访问的组件路径，如：`system/user/index`，默认在`views`目录下" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  组件路径
                </span>
              </template>
              <el-input v-model="form.component" placeholder="请输入组件路径" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'M' && form.menuType !== 'A'" :span="12">
            <el-form-item>
              <el-input v-model="form.perms" placeholder="请输入权限标识" maxlength="100" />
              <template #label>
                <span>
                  <el-tooltip content="控制器中定义的权限字符，如：@SaCheckPermission('system:user:list')" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  权限字符
                </span>
              </template>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item>
              <el-input v-model="form.queryParam" placeholder="请输入路由参数" maxlength="255" />
              <template #label>
                <span>
                  <el-tooltip content='访问路由的默认传递参数，如：`{"id": 1, "name": "ry"}`' placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  路由参数
                </span>
              </template>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item>
              <template #label>
                <span>
                  <el-tooltip content="选择是则会被`keep-alive`缓存，需要匹配组件的`name`和地址保持一致" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  是否缓存
                </span>
              </template>
              <el-radio-group v-model="form.isCache">
                <el-radio value="0">缓存</el-radio>
                <el-radio value="1">不缓存</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item>
              <template #label>
                <span>
                  <el-tooltip content="选择隐藏则路由将不会出现在侧边栏，但仍然可以访问" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  显示状态
                </span>
              </template>
              <el-radio-group v-model="form.visible">
                <el-radio v-for="dict in sys_show_hide" :key="dict.value" :value="dict.value">{{ dict.label }} </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item>
              <template #label>
                <span>
                  <el-tooltip content="选择停用则路由将不会出现在侧边栏，也不能被访问" placement="top">
                    <el-icon>
                      <question-filled />
                    </el-icon>
                  </el-tooltip>
                  菜单状态
                </span>
              </template>
              <el-radio-group v-model="form.status">
                <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
                  {{ dict.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- ln_pro 独有扩展字段（sys_menu_ext），默认折叠 -->
        <el-collapse v-model="extCollapseActive" class="mt-2">
          <el-collapse-item title="扩展信息" name="ext">
            <el-row>
              <el-col :span="12">
                <el-form-item label="是否路由菜单">
                  <el-select v-model="form.ext.isRoute" placeholder="是否路由菜单" clearable>
                    <el-option label="是" :value="1" />
                    <el-option label="否" :value="0" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="是否缓存路由">
                  <el-select v-model="form.ext.keepAlive" placeholder="是否缓存路由" clearable>
                    <el-option label="是" :value="1" />
                    <el-option label="否" :value="0" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="默认跳转地址">
                  <el-input v-model="form.ext.redirectUrl" placeholder="请输入默认跳转地址" maxlength="255" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="打开方式">
                  <el-select v-model="form.ext.internalExternal" placeholder="打开方式" clearable>
                    <el-option label="内部" :value="0" />
                    <el-option label="外部" :value="1" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="类名">
                  <el-input v-model="form.ext.className" placeholder="请输入类名（前端组件 class）" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="12">
                <el-form-item label="是否记录日志">
                  <el-input-number v-model="form.ext.rememberLog" :min="0" controls-position="right" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="日志字符串">
                  <el-input v-model="form.ext.logString" placeholder="请输入日志字符串" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row>
              <el-col :span="24">
                <el-form-item label="桌面配置 XML">
                  <el-input v-model="form.ext.config" type="textarea" placeholder="请输入桌面布局配置 XML（老系统）" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    <!-- 添加或修改OSS对象存储对话框 -->
    <el-dialog v-model="uploadDialog.visible" :title="uploadDialog.title" width="500px" append-to-body>
      <el-upload
        ref="uploadRef"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="headers"
        :data="uploadData"
        :action="uploadMode === '1' ? uploadMenuFileUrl : uploadMenuFileByIdUrl"
        :disabled="isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleUploadSuccess"
        :auto-upload="false"
        drag
      >
        <el-icon class="el-icon--upload">
          <i-ep-upload-filled />
        </el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip" style="text-align: left; line-height: 1.7">
            <div>菜单将导入到本次所选位置；模板为空白表格，请按表头批注填写后再上传。</div>
            <div>如需复制整棵菜单树，请使用列表中的「导出」，不要手工粘贴数据。</div>
            <div class="text-center">
              <span>仅允许导入xls、xlsx格式文件。</span>
              <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="handleDownloadTem">下载模板</el-link>
            </div>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitUpload">确 定</el-button>
          <el-button @click="cancelUpload">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="deleteDialog.visible" :title="deleteDialog.title" destroy-on-close append-to-bod width="750px">
      <el-tree
        ref="menuTreeRef"
        class="tree-border"
        :data="menuOptions"
        show-checkbox
        node-key="menuId"
        :check-strictly="false"
        empty-text="加载中，请稍候"
        :default-expanded-keys="[0]"
        :props="{ value: 'menuId', label: 'menuName', children: 'children' } as any"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitDeleteForm" :loading="deleteLoading">确 定</el-button>
          <el-button @click="cancelCascade">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Menu" lang="ts">
import { addMenu, cascadeDelMenu, delMenu, getMenu, listMenu, updateMenu } from '@/api/system/menu';
import { listClient } from '@/api/system/client';
import { MenuForm, MenuQuery, MenuVO } from '@/api/system/menu/types';
import { MenuTypeEnum } from '@/enums/MenuTypeEnum';
import type { UploadInstance } from 'element-plus';
import { globalHeaders } from '@/utils/request';

interface MenuOptionsType {
  menuId: number;
  menuName: string;
  children: MenuOptionsType[] | undefined;
}

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_show_hide, sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_show_hide', 'sys_normal_disable'));

const menuList = ref<MenuVO[]>([]);
const menuChildrenListMap = ref({});
const menuExpandMap = ref({});
const loading = ref(true);
const showSearch = ref(true);
const menuOptions = ref<MenuOptionsType[]>([]);
const clientOptions = ref<any[]>([]);
const iconType = ref('ICON');

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const baseUrl = import.meta.env.VITE_APP_BASE_API;
const uploadMenuFileUrl = `${baseUrl}/system/menu/importData`;
const uploadMenuFileByIdUrl = `${baseUrl}/system/menu/importDataByMenuId`;
const uploadRef = ref<UploadInstance>();
const headers = ref(globalHeaders());
const uploadMode = ref('1'); // 1 表示总菜单数据，2 表示单个菜单子菜单数据
const isUploading = ref(false);
// 文件上传额外参数
const uploadData = ref<any>({
  parentMenuId: undefined,
  updateSupport: false
});
const uploadDialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const queryFormRef = ref<ElFormInstance>();
const menuFormRef = ref<ElFormInstance>();
/** 扩展信息折叠面板激活项（默认折叠） */
const extCollapseActive = ref<string[]>([]);
const initFormData = {
  path: '',
  menuId: undefined,
  parentId: 0,
  menuName: '',
  icon: '',
  menuType: MenuTypeEnum.M,
  orderNum: 1,
  isFrame: '1',
  isCache: '0',
  visible: '0',
  status: '0',
  clientId: '',
  appId: undefined,
  // 扩展信息默认空对象；提交前由 cleanupEmptyExt 决定是否清空
  ext: {}
};
const data = reactive<PageData<MenuForm, MenuQuery>>({
  form: { ...initFormData },
  queryParams: {
    menuName: undefined,
    status: undefined
  },
  rules: {
    menuName: [{ required: true, message: '菜单名称不能为空', trigger: 'blur' }],
    orderNum: [{ required: true, message: '菜单顺序不能为空', trigger: 'blur' }],
    path: [{ required: true, message: '路由地址不能为空', trigger: 'blur' }],
    clientId: [{ required: true, message: '客户端绑定不能为空', trigger: 'change' }]
  }
});

const menuTableRef = ref<ElTableInstance>();

const { queryParams, form, rules } = toRefs<PageData<MenuForm, MenuQuery>>(data);

/** 获取子菜单列表 */
const getChildrenList = async (row: any, treeNode: unknown, resolve: (data: any[]) => void) => {
  menuExpandMap.value[row.menuId] = { row, treeNode, resolve };
  const children = menuChildrenListMap.value[row.menuId] || [];
  // 菜单的子菜单清空后关闭展开
  if (children.length == 0) {
    // fix: 处理当菜单只有一个子菜单并被删除，需要将父菜单的展开状态关闭
    menuTableRef.value?.updateKeyChildren(row.menuId, children);
  }
  resolve(children);
};

/** 收起菜单时从menuExpandMap中删除对应菜单id数据 */
const expandMenuHandle = async (row: any, expanded: boolean) => {
  if (!expanded) {
    menuExpandMap.value[row.menuId] = undefined;
  }
};

/** 刷新展开的菜单数据 */
const refreshLoadTree = (parentId: string | number) => {
  if (menuExpandMap.value[parentId]) {
    const { row, treeNode, resolve } = menuExpandMap.value[parentId];
    if (row) {
      getChildrenList(row, treeNode, resolve);
      if (row.parentId) {
        const grandpaMenu = menuExpandMap.value[row.parentId];
        getChildrenList(grandpaMenu.row, grandpaMenu.treeNode, grandpaMenu.resolve);
      }
    }
  }
};

/** 重新加载所有已展开的菜单的数据 */
const refreshAllExpandMenuData = () => {
  for (const menuId in menuExpandMap.value) {
    refreshLoadTree(menuId);
  }
};

/** 查询菜单列表 */
const getList = async () => {
  loading.value = true;
  const res = await listMenu(queryParams.value);

  const tempMap = {};
  // 存储 父菜单:子菜单列表
  for (const menu of res.data) {
    const parentId = menu.parentId;
    if (!tempMap[parentId]) {
      tempMap[parentId] = [];
    }
    tempMap[parentId].push(menu);
  }
  // 创建一个当前所有 menuId 的 Set，用于查找父菜单是否存在于当前数据中
  const menuIdSet = new Set();
  // 设置有没有子菜单
  for (const menu of res.data) {
    menu['hasChildren'] = tempMap[menu.menuId]?.length > 0;
    menuIdSet.add(menu.menuId);
  }
  menuChildrenListMap.value = tempMap;
  // 找出所有父ID不在当前菜单ID集合中的菜单项，作为新的顶层菜单
  menuList.value = res.data.filter((menu) => !menuIdSet.has(menu.parentId));
  // 根据新数据重新加载子菜单数据
  refreshAllExpandMenuData();
  loading.value = false;
};
/** 查询菜单下拉树结构 */
const getTreeselect = async () => {
  menuOptions.value = [];
  const response = await listMenu();
  const menu: MenuOptionsType = { menuId: 0, menuName: '主类目', children: [] };
  menu.children = proxy?.handleTree<MenuOptionsType>(response.data, 'menuId');
  menuOptions.value.push(menu);
};
/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
};
/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  iconType.value = 'ICON';
  menuFormRef.value?.resetFields();
};

/** 搜索按钮操作 */
const handleQuery = () => {
  getList();
};
/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};
/** 新增按钮操作 */
const handleAdd = (row?: MenuVO) => {
  reset();
  getTreeselect();
  row && row.menuId ? (form.value.parentId = row.menuId) : (form.value.parentId = 0);
  dialog.visible = true;
  dialog.title = '添加菜单';
};
/** 修改按钮操作 */
const handleUpdate = async (row: MenuVO) => {
  reset();
  await getTreeselect();
  if (row.menuId) {
    const { data } = await getMenu(row.menuId);
    form.value = data;
    // 后端无 ext 数据时为 null，v-model 需 object 才能绑定，置空对象
    form.value.ext = data.ext ?? {};
  }
  if (form.value.icon.includes('http')) {
    iconType.value = 'UPLOAD';
  }
  dialog.visible = true;
  dialog.title = '修改菜单';
};
/** 提交按钮 */
const submitForm = () => {
  menuFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      // 扩展字段全空时清掉 ext，避免后端写入空记录
      cleanupEmptyExt(form.value.ext);
      form.value.menuId ? await updateMenu(form.value) : await addMenu(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    }
  });
};

/**
 * 清理空的扩展对象。
 * 当所有扩展字段都为空时，从 form 中删除 ext 属性，
 * 让后端按"不动 sys_menu_ext"语义处理，避免写入全空记录。
 */
const cleanupEmptyExt = (ext: Record<string, any> | undefined): void => {
  if (!ext) return;
  const hasValue = Object.values(ext).some((v) => v !== '' && v !== null && v !== undefined);
  if (!hasValue) {
    delete (form.value as any).ext;
  }
};
/** 删除按钮操作 */
const handleDelete = async (row: MenuVO) => {
  await proxy?.$modal.confirm('是否确认删除名称为"' + row.menuName + '"的数据项?');
  await delMenu(row.menuId);
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

const deleteLoading = ref<boolean>(false);
const menuTreeRef = ref<ElTreeInstance>();

const deleteDialog = reactive<DialogOption>({
  visible: false,
  title: '级联删除菜单'
});

/** 级联删除按钮操作 */
const handleCascadeDelete = () => {
  menuTreeRef.value?.setCheckedKeys([]);
  getTreeselect();
  deleteDialog.visible = true;
};

/** 取消按钮 */
const cancelCascade = () => {
  menuTreeRef.value?.setCheckedKeys([]);
  deleteDialog.visible = false;
};

/** 删除提交按钮 */
const submitDeleteForm = async () => {
  const menuIds = menuTreeRef.value?.getCheckedKeys();
  if (menuIds.length < 0) {
    proxy?.$modal.msgWarning('请选择要删除的菜单');
    return;
  }

  deleteLoading.value = true;
  await cascadeDelMenu(menuIds).finally(() => (deleteLoading.value = false));
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
  deleteDialog.visible = false;
};

// const getClientOptions = async () => {
//   const data = await listClient();
//   clientOptions.value = data.rows.map((item: any) => ({
//     label: item.clientKey,
//     value: item.clientId
//   }));
// };

const handleImport = () => {
  // 顶部「导入」= 总菜单数据导入，必须显式重置模式与参数，
  // 否则会用上一次行内导入（mode=2）的 parentMenuId 提交到 /importDataByMenuId
  uploadMode.value = '1';
  uploadData.value = {
    parentMenuId: undefined,
    updateSupport: false
  };
  uploadDialog.visible = true;
};

const handleExport = () => {
  proxy?.download('system/menu/export', {}, `菜单_${new Date().getTime()}.xlsx`);
};

const handleExportById = (row: any) => {
  proxy?.download('system/menu/exportByMenuId', { menuId: row.menuId }, `菜单_${new Date().getTime()}.xlsx`);
};

const handleDownloadTem = () => {
  // 两种导入模式共用空白模板（含表头批注），不再带当前菜单的示例行
  proxy?.download('system/menu/importTemplate', {}, `菜单模板_${new Date().getTime()}.xlsx`);
};

const submitUpload = () => {
  uploadRef.value!.submit();
};

const handleUploadError = (err: any) => {
  proxy?.$modal.msgError(err.message);
};

const handleFileUploadProgress = () => {
  isUploading.value = true;
};

const handleUploadSuccess = (res: any) => {
  isUploading.value = false;
  uploadRef.value?.handleRemove(file);
  cancelUpload();
  ElMessageBox.alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + res.msg + '</div>', '导入结果', {
    dangerouslyUseHTMLString: true
  });
  getList();
};

const handleUploadById = (row: any) => {
  uploadMode.value = '2';
  uploadDialog.visible = true;
  uploadData.value = {
    parentMenuId: row.menuId,
    updateSupport: true
  };
};

const uploadReset = () => {
  uploadData.value = {
    parentMenuId: undefined,
    // 不能留 undefined：el-upload 会把 undefined 拼成字符串 "undefined" 提交，
    // 后端 boolean updateSupport 绑定会直接 400
    updateSupport: false
  };
  uploadMode.value = '1';
  uploadRef.value!.clearFiles();
};

const cancelUpload = () => {
  uploadReset();
  uploadDialog.visible = false;
};

onMounted(() => {
  getList();
  // getClientOptions();
});
</script>

<style scoped lang="scss">
.tree-border {
  height: 300px;
  overflow: auto;
}
</style>
