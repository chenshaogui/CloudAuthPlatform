<template>
  <div class="p-2">
    <el-row :gutter="20">
      <!-- 部门树 -->
      <el-col :lg="4" :xs="24">
        <el-card shadow="hover">
          <el-tree
            ref="deptTreeRef"
            class="mt-2"
            node-key="value"
            :data="applicationList"
            :props="{ label: 'label', children: 'children' } as any"
            :expand-on-click-node="false"
            highlight-current
            default-expand-all
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <div class="custom-tree-node">
                <div class="custom-tree-node-label">
                  <!-- 已绑客户端：绿色小圆点标识；点击 🔗 跳转到客户端详情 -->
                  <span v-if="data.clientId" class="bound-indicator is-bound"></span>
                  <span class="node-label-text">{{ node.label }}</span>
                  <el-tooltip
                    v-if="data.clientId"
                    :content="`已绑定客户端 ${data.clientId}，点击查看/解绑`"
                    placement="top"
                  >
                    <el-link
                      type="success"
                      :underline="false"
                      icon="Link"
                      class="bound-link"
                      @click.stop="handleGoClient(data.clientId)"
                    />
                  </el-tooltip>
                </div>
                <!-- 应用中心为虚拟根节点（menuId=0），不允许编辑/删除；操作按钮 hover 时浮现 -->
                <div v-if="data.menuId !== 0" class="node-actions">
                  <el-tooltip content="修改" placement="top">
                    <el-button v-hasPermi="['system:application:update']" link type="primary" icon="Edit" @click="handleUpdateMain(data)"></el-button>
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button
                      v-hasPermi="['system:application:remove']"
                      link
                      type="danger"
                      icon="Delete"
                      @click="handleDelete(data, 'A')"
                    ></el-button>
                  </el-tooltip>
                </div>
              </div>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      <el-col :lg="20" :xs="24">
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
                <el-button v-hasPermi="['system:application:add']" type="primary" plain icon="Plus" @click="handleAdd(null, 'M')">新增</el-button>
              </el-col>
              <el-col :span="1.5">
                <el-button
                  v-hasPermi="['system:application:upload']"
                  type="success"
                  plain
                  icon="Upload"
                  @click="handleUploadById({ menuId: queryParams.menuId })"
                  >导入</el-button
                >
              </el-col>
              <el-col :span="1.5">
                <el-button
                  v-hasPermi="['system:application:download']"
                  type="info"
                  plain
                  icon="Download"
                  @click="handleExportById({ menuId: queryParams.menuId })"
                  >导出</el-button
                >
              </el-col>
              <el-col :span="3">
                <el-button v-hasPermi="['system:application:remove', 'system:menu:remove']" type="danger" plain icon="Delete" @click="handleCascadeDelete">级联删除</el-button>
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
                  <el-button v-hasPermi="['system:application:update']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)" />
                </el-tooltip>
                <el-tooltip content="新增" placement="top">
                  <el-button v-hasPermi="['system:application:add']" link type="primary" icon="Plus" @click="handleAdd(scope.row, 'C')" />
                </el-tooltip>
                <el-tooltip content="导入" placement="top" v-if="scope.row.parentId === queryParams.menuId">
                  <el-button v-hasPermi="['system:application:upload']" link type="success" icon="Upload" @click="handleUploadById(scope.row)" />
                </el-tooltip>
                <el-tooltip content="导出" placement="top" v-if="scope.row.parentId === queryParams.menuId">
                  <el-button v-hasPermi="['system:application:download']" link type="info" icon="Download" @click="handleExportById(scope.row)" />
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button v-hasPermi="['system:application:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)" />
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
                  <template v-if="addType === 'A'">
                    <el-tree-select
                      v-model="form.parentId"
                      :data="menuOptions"
                      :props="{ value: 'menuId', label: 'menuName', children: 'children' } as any"
                      value-key="menuId"
                      placeholder="选择上级菜单"
                      check-strictly
                      disabled
                    />
                  </template>
                  <template v-else>
                    <el-tree-select
                      v-model="form.parentId"
                      :data="
                        addType === 'M' || (dialog.title.includes('修改') && form.menuType === 'M' && form.parentId === queryParams.menuId)
                          ? applicationList
                          : childMenuList
                      "
                      :props="{ value: 'menuId', label: 'menuName', children: 'children' } as any"
                      value-key="menuId"
                      placeholder="选择上级菜单"
                      check-strictly
                      disabled
                    />
                  </template>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="菜单类型" prop="menuType">
                  <el-radio-group v-model="form.menuType">
                    <template v-if="addType === 'A'">
                      <el-radio value="A">应用</el-radio>
                    </template>
                    <template v-else>
                      <el-radio value="M">目录</el-radio>
                      <el-radio value="C">菜单</el-radio>
                      <el-radio value="F">按钮</el-radio>
                    </template>
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
          <el-form ref="ossFormRef" :model="form" :rules="rules" label-width="80px">
            <el-upload
              ref="uploadRef"
              :limit="1"
              accept=".xlsx, .xls"
              :headers="headers"
              :data="uploadData"
              :action="uploadMenuFileByIdUrl"
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
                  <div>菜单将导入到本次所选菜单下；模板为空白表格，请按表头批注填写后再上传。</div>
                  <div>如需复制整棵菜单树，请使用列表中的「导出」，不要手工粘贴数据。</div>
                  <div class="text-center">
                    <span>仅允许导入xls、xlsx格式文件。</span>
                    <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="handleDownloadTem"
                      >下载模板</el-link
                    >
                  </div>
                </div>
              </template>
            </el-upload>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button type="primary" @click="submitUpload">确 定</el-button>
              <el-button @click="cancelUpload">取 消</el-button>
            </div>
          </template>
        </el-dialog>

        <el-dialog v-model="deleteDialog.visible" :title="deleteDialog.title" destroy-on-close append-to-bod width="750px">
          <div class="mb-[10px] text-sm text-gray-500">
            勾选应用节点会联动选中其全部子菜单（含界面未展示的历史数据）；删除会同时清理角色授权，且不可恢复。若应用已绑定客户端，请先在【客户端管理】解绑。
          </div>
          <el-tree
            ref="menuTreeRef"
            class="tree-border"
            :data="deleteTreeData"
            show-checkbox
            node-key="menuId"
            :check-strictly="false"
            empty-text="暂无可删除的菜单"
            default-expand-all
            :props="{ value: 'menuId', label: 'menuName', children: 'children' } as any"
          />
          <template #footer>
            <div class="dialog-footer">
              <el-button type="primary" @click="submitDeleteForm" :loading="deleteLoading">确 定</el-button>
              <el-button @click="cancelCascade">取 消</el-button>
            </div>
          </template>
        </el-dialog>
      </el-col>
    </el-row>
  </div>
</template>

<script setup name="Application" lang="ts">
import { addMenu, cascadeDelMenu, delMenu, getMenu, updateMenu, getApplicationsApi } from '@/api/system/menu';
import { listByMenuIdApi, listSubtreeByMenuIdApi } from '@/api/system/application';
import { useRouter } from 'vue-router';

const router = useRouter();

import { MenuForm, MenuQuery, MenuVO } from '@/api/system/application/types';
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
const childMenuList = ref<MenuVO[]>([]);
const menuChildrenListMap = ref({});
const menuExpandMap = ref({});
const loading = ref(true);
const showSearch = ref(true);
const menuOptions = ref<MenuOptionsType[]>([]);
const deleteTreeData = ref<any[]>([]);
const iconType = ref('ICON');
const applicationList = ref([]);
const deptTreeRef = ref<ElTreeInstance>();
const addType = ref('M'); // M标识目录 C标识子集

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const baseUrl = import.meta.env.VITE_APP_BASE_API;
const uploadMenuFileByIdUrl = `${baseUrl}/system/menu/importDataByMenuId`;
const uploadRef = ref<UploadInstance>();
const headers = ref(globalHeaders());
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
  appId: undefined
};
const data = reactive<PageData<MenuForm, MenuQuery>>({
  form: { ...initFormData },
  queryParams: {
    menuName: undefined,
    status: undefined,
    menuId: undefined
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

/** 查询菜单列表 */
const getList = async () => {
  loading.value = true;
  try {
    const res = await listByMenuIdApi(queryParams.value);

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
  } finally {
    // 无论成功失败都停止 loading，避免接口异常时表格一直转圈
    loading.value = false;
  }
};

/** 查询菜单列表 */
const getChildList = async (menuId) => {
  const res = await listByMenuIdApi({ menuId: menuId || queryParams.value.menuId });

  // 同步重建父→子映射，给菜单项补 hasChildren（其它地方可能还会用到 menuChildrenListMap）
  const tempMap = {};
  for (const menu of res.data) {
    const parentId = menu.parentId;
    if (!tempMap[parentId]) tempMap[parentId] = [];
    tempMap[parentId].push(menu);
  }
  for (const menu of res.data) {
    menu['hasChildren'] = (tempMap[menu.menuId]?.length ?? 0) > 0;
  }
  menuChildrenListMap.value = tempMap;

  // 关键：上级菜单下拉要传【完整树】，el-tree-select 才能把 form.parentId 解析成名称
  // 之前 filter(!menuIdSet.has(parentId)) 只留顶层节点，导致大部分 menuId 无法在选项里命中
  // → el-tree-select 把原始 parentId 数字渲染出来，看上去「只显示编号」
  const tree = proxy?.handleTree(res.data, 'menuId') ?? [];

  // 应用根（menu_type='A'）被 listByMenuId 服务端 .ne(menuId, appMenuId) 过滤掉，
  // 但顶级菜单（直属应用根的子项）的 parentId 正好等于应用根 menuId，
  // 必须把应用根作为根节点塞回去，否则顶级菜单的上级菜单也只能显示编号
  const appMenuId = menuId || queryParams.value.menuId;
  const appRoot = applicationList.value?.[0]?.children?.find((a) => a.menuId === appMenuId);
  childMenuList.value = appRoot ? [{ ...appRoot, children: tree }] : tree;
};

const getApplicationsList = async () => {
  const res = await getApplicationsApi();
  const menu = { menuId: 0, menuName: '应用中心', value: 0, label: '应用中心', children: [] };
  const list = res.data || [];
  const data = list.map((item) => ({
    ...item,
    value: item.menuId,
    label: item.menuName
  }));
  menu.children = data;
  applicationList.value = [menu];
  // 无应用数据时兜底，避免 res.data[0] 越界导致后续 getList 不执行（表格一直 loading）
  if (list.length > 0) {
    queryParams.value.menuId = list[0].menuId;
    deptTreeRef.value?.setCurrentKey(list[0].menuId);
  } else {
    queryParams.value.menuId = undefined;
  }
};

/** 查询菜单下拉树结构 */
const getTreeselect = async () => {
  menuOptions.value = [];
  const response = await getApplicationsApi();
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
const handleAdd = (row?: MenuVO, type?: string) => {
  reset();
  addType.value = type;
  if (type === 'C') {
    getChildList(row?.parentId);
  } else {
    getChildList(queryParams.value.menuId);
  }
  row && row.menuId ? (form.value.parentId = row.menuId) : (form.value.parentId = 0);
  if (!form.value.parentId) {
    form.value.parentId = queryParams.value.menuId;
  }
  dialog.visible = true;
  dialog.title = '添加菜单';
};

const handleUpdateMain = async (row: MenuVO) => {
  reset();
  addType.value = 'A';
  getTreeselect();
  if (row.menuId) {
    const { data } = await getMenu(row.menuId);
    form.value = data;
  }
  // icon 可能为 null（应用未配图标），直接 includes 会抛错导致弹窗不出现
  if ((form.value.icon || '').includes('http')) {
    iconType.value = 'UPLOAD';
  }
  dialog.visible = true;
  dialog.title = '修改菜单';
};

/** 修改按钮操作 */
const handleUpdate = async (row: MenuVO) => {
  reset();
  addType.value = 'C';
  await getChildList(queryParams.value.menuId);
  if (row.menuId) {
    const { data } = await getMenu(row.menuId);
    form.value = data;
  }
  // icon 可能为 null（菜单未配图标），直接 includes 会抛错导致弹窗不出现
  if ((form.value.icon || '').includes('http')) {
    iconType.value = 'UPLOAD';
  }
  dialog.visible = true;
  dialog.title = '修改菜单';
};
/** 提交按钮 */
const submitForm = () => {
  menuFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      form.value.menuId ? await updateMenu(form.value) : await addMenu(form.value);
      proxy?.$modal.msgSuccess('操作成功');
      dialog.visible = false;
      await getList();
    }
  });
};
/** 删除按钮操作 */
const handleDelete = async (row: MenuVO, type?: string) => {
  await proxy?.$modal.confirm('是否确认删除名称为"' + row.menuName + '"的数据项?');
  await delMenu(row.menuId);
  if (type === 'A') {
    await getApplicationsList();
  }
  await getList();
  proxy?.$modal.msgSuccess('删除成功');
};

const deleteLoading = ref<boolean>(false);
const menuTreeRef = ref<ElTreeInstance>();

const deleteDialog = reactive<DialogOption>({
  visible: false,
  title: '级联删除菜单'
});

/** 级联删除按钮操作：加载当前应用按 parent_id 递归的完整子树 */
const handleCascadeDelete = async () => {
  const appMenuId = queryParams.value.menuId;
  if (!appMenuId) {
    proxy?.$modal.msgWarning('请先在左侧选择要删除的应用');
    return;
  }
  const res = await listSubtreeByMenuIdApi(appMenuId);
  deleteTreeData.value = proxy?.handleTree(res.data || [], 'menuId') ?? [];
  menuTreeRef.value?.setCheckedKeys([]);
  deleteDialog.visible = true;
};

/** 取消按钮 */
const cancelCascade = () => {
  menuTreeRef.value?.setCheckedKeys([]);
  deleteDialog.visible = false;
};

/** 删除提交按钮 */
const submitDeleteForm = async () => {
  const menuIds = menuTreeRef.value?.getCheckedKeys() ?? [];
  if (menuIds.length === 0) {
    proxy?.$modal.msgWarning('请选择要删除的菜单');
    return;
  }

  deleteLoading.value = true;
  try {
    await cascadeDelMenu(menuIds);
    // 删掉当前应用时重新加载左侧应用树并切换选中项
    if (menuIds.some((id) => String(id) === String(queryParams.value.menuId))) {
      queryParams.value.menuId = undefined;
      await getApplicationsList();
    }
    if (queryParams.value.menuId) {
      await getList();
    } else {
      menuList.value = [];
    }
    proxy?.$modal.msgSuccess('删除成功');
    deleteDialog.visible = false;
  } finally {
    deleteLoading.value = false;
  }
};

const handleExportById = (row: any) => {
  proxy?.download('system/menu/exportByMenuId', { menuId: row.menuId }, `菜单_${new Date().getTime()}.xlsx`);
};

const handleDownloadTem = () => {
  // 模板统一为空白表格（含表头批注），不再带当前菜单的示例行，避免误当成要导入的数据
  proxy?.download('system/menu/importTemplate', {}, `菜单模板_${new Date().getTime()}.xlsx`);
};

const submitUpload = () => {
  uploadRef.value!.submit();
};

const handleUploadError = (err: any) => {
  proxy?.$modal.msgError(err.message);
};

/**文件上传中处理 */
const handleFileUploadProgress = () => {
  isUploading.value = true;
};

const handleUploadSuccess = (res: any, file: UploadFile) => {
  isUploading.value = false;
  uploadRef.value?.handleRemove(file);
  cancelUpload();
  ElMessageBox.alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + res.msg + '</div>', '导入结果', {
    dangerouslyUseHTMLString: true
  });
  getList();
};

const handleUploadById = (row: any) => {
  uploadDialog.visible = true;
  uploadData.value = {
    parentMenuId: row.menuId,
    updateSupport: true
  };
};

const uploadReset = () => {
  uploadData.value = {
    parentMenuId: undefined,
    updateSupport: false
  };
  uploadRef.value!.clearFiles();
};

const cancelUpload = () => {
  uploadReset();
  uploadDialog.visible = false;
};

const handleNodeClick = (row: any) => {
  queryParams.value.menuId = row.menuId;
  getList();
};

/** 跳转到客户端详情（已绑定应用 → 查看/解绑） */
const handleGoClient = (clientId: string) => {
  router.push({ path: '/system/client', query: { clientId } });
};

onMounted(async () => {
  try {
    await getApplicationsList();
  } catch (e) {
    console.error('加载应用列表失败', e);
  }
  await getList();
});
</script>

<style lang="scss" scoped>
:deep(.el-tree-node__content) {
  border-radius: 4px;
  transition: background-color 0.15s;
  &:hover {
    background-color: #f5f7fa;
  }
}
.custom-tree-node {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.custom-tree-node-label {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}
.node-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.bound-indicator {
  flex-shrink: 0;
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background-color: #67c23a;
  box-shadow: 0 0 0 2px rgba(103, 194, 58, 0.18);
}
.bound-link {
  flex-shrink: 0;
  margin-left: 4px;
  vertical-align: middle;
}
.node-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-left: 4px;
  opacity: 0;
  transition: opacity 0.15s;
  :deep(.el-button + .el-button) {
    margin-left: 2px;
  }
}
.custom-tree-node:hover .node-actions {
  opacity: 1;
}
</style>
