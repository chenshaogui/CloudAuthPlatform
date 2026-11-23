<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="search">
        <el-form ref="queryFormRef" :model="queryParams" :inline="true" label-width="85px">
          <el-form-item label="客户端名称" prop="clientKey">
            <el-input v-model="queryParams.clientKey" placeholder="请输入客户端名称" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="客户端秘钥" prop="clientSecret">
            <el-input v-model="queryParams.clientSecret" placeholder="请输入客户端秘钥" clearable @keyup.enter="handleQuery" />
          </el-form-item>
          <el-form-item label="状态" prop="status">
            <el-select v-model="queryParams.status" placeholder="状态" clearable>
              <el-option v-for="dict in sys_normal_disable" :key="dict.value" :label="dict.label" :value="dict.value" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
            <el-button icon="Refresh" @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </transition>

    <el-card shadow="never">
      <template #header>
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:client:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:client:edit']" type="success" plain icon="Edit" :disabled="single" @click="handleUpdate()">
              修改
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:client:remove']" type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">
              删除
            </el-button>
          </el-col>
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:client:export']" type="warning" plain icon="Download" @click="handleExport">导出</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" :data="clientList" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column v-if="true" label="id" align="center" prop="id" />
        <el-table-column label="客户端id" align="center" prop="clientId" />
        <el-table-column label="客户端名称" align="center" prop="clientKey" />
        <el-table-column label="客户端秘钥" align="center" prop="clientSecret" />
        <el-table-column label="授权类型" align="center">
          <template #default="scope">
            <dict-tag :options="sys_grant_type" :value="scope.row.grantTypeList" />
          </template>
        </el-table-column>
        <el-table-column label="设备类型" align="center">
          <template #default="scope">
            <dict-tag :options="sys_device_type" :value="scope.row.deviceType" />
          </template>
        </el-table-column>
        <el-table-column label="允许授权地址" align="center" prop="allowUrl" />
        <el-table-column label="Token活跃超时时间" align="center" prop="activeTimeout" />
        <el-table-column label="Token固定超时时间" align="center" prop="timeout" />
        <el-table-column label="签名Key" align="center" prop="signKey" />
        <el-table-column key="status" label="状态" align="center">
          <template #default="scope">
            <el-switch v-model="scope.row.status" active-value="0" inactive-value="1" @change="handleStatusChange(scope.row)"></el-switch>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button v-hasPermi="['system:client:edit']" link type="primary" icon="Edit" @click="handleUpdate(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="绑定" placement="top">
              <el-button v-hasPermi="['system:client:remove']" link type="primary" icon="Checked" @click="handleBinding(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button v-hasPermi="['system:client:remove']" link type="primary" icon="Delete" @click="handleDelete(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>
    <!-- 添加或修改客户端管理对话框 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="600px" append-to-body>
      <el-form ref="clientFormRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="客户端名称" prop="clientKey">
          <el-input v-model="form.clientKey" :disabled="form.id != null" placeholder="请输入客户端名称" />
        </el-form-item>
        <el-form-item label="客户端秘钥" prop="clientSecret">
          <el-input v-model="form.clientSecret" :disabled="form.id != null" placeholder="请输入客户端秘钥">
            <template #append>
              <el-button @click="generateClientSecret" :disabled="form.id != null">生成</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="授权类型" prop="grantTypeList">
          <el-select v-model="form.grantTypeList" multiple placeholder="请输入授权类型">
            <el-option v-for="dict in sys_grant_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型" prop="deviceType">
          <el-select v-model="form.deviceType" placeholder="请输入设备类型">
            <el-option v-for="dict in sys_device_type" :key="dict.value" :label="dict.label" :value="dict.value"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="允许授权地址" prop="allowUrl" label-width="auto">
          <el-input v-model="form.allowUrl" placeholder="请输入允许授权地址" />
        </el-form-item>
        <el-form-item label="布局配置" label-width="auto">
          <div class="layout-config-editor">
            <el-alert type="info" :closable="false" show-icon class="layout-config-hint">
              <template #title>
                选择 <b>default</b> 模板时，前端走硬编码默认样式，<b>布局配置可不填</b>（留空即可）；
                选择 <b>liaoyuntong</b> 等自定义模板时，需要在此写入该模板的样式/字段配置。
              </template>
            </el-alert>
            <div class="layout-config-toolbar">
              <span class="layout-config-label">模板预设：</span>
              <el-select v-model="layoutConfigPreset" placeholder="选择模板后点击「插入默认配置」覆盖当前 JSON" clearable style="width: 220px" @change="onPresetChange">
                <el-option v-for="name in templateNames" :key="name" :label="name" :value="name" />
              </el-select>
              <el-button :disabled="!layoutConfigPreset" @click="applyPreset">插入默认配置</el-button>
              <el-button :disabled="!layoutConfigPreset" @click="setTemplateOnly">仅设置 template 字段</el-button>
              <el-button :disabled="!form.layoutConfig" @click="formatLayoutConfig">格式化 JSON</el-button>
              <span class="layout-config-status" :class="{ 'is-ok': layoutConfigOk, 'is-err': !layoutConfigOk && layoutConfigJsonError }">
                <template v-if="!form.layoutConfig">未配置（前端走硬编码默认）</template>
                <template v-else-if="layoutConfigOk">JSON 有效 · template = {{ parsedTemplate || '(空)' }}</template>
                <template v-else>JSON 无效：{{ layoutConfigJsonError }}</template>
              </span>
            </div>
            <el-input
              v-model="form.layoutConfig"
              type="textarea"
              :autosize="{ minRows: 10, maxRows: 24 }"
              placeholder='示例：&#10;{&#10;  "template": "default",&#10;  "version": 1,&#10;  "login": { "title": "...", "backgroundImage": "..." },&#10;  "register": { ... },&#10;  "resetPwd": { ... }&#10;}'
              spellcheck="false"
              @blur="validateLayoutConfig"
            />
          </div>
        </el-form-item>
        <el-form-item prop="registerEnabled" label-width="auto">
          <template #label>
            <span>
              <el-tooltip content="关闭后该客户端的登录页不显示「立即注册」入口，注册接口也会拒绝该 client" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
              开放注册
            </span>
          </template>
          <el-radio-group v-model="form.registerEnabled">
            <el-radio value="1">开放</el-radio>
            <el-radio value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="resetPwdEnabled" label-width="auto">
          <template #label>
            <span>
              <el-tooltip content="关闭后该客户端的登录页不显示「修改密码」入口" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
              开放重置密码
            </span>
          </template>
          <el-radio-group v-model="form.resetPwdEnabled">
            <el-radio value="1">开放</el-radio>
            <el-radio value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="应用绑定类型">
          <el-radio-group v-model="menuType">
            <el-radio value="N">无应用</el-radio>
            <el-radio value="H">绑定应用</el-radio>
            <el-radio value="C" v-if="!form.id">新增应用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="menuType === 'H'" label="绑定应用" prop="menuId">
          <el-select v-model="form.menuId" placeholder="请选择绑定应用">
            <el-option v-for="item in appList" :key="item.menuId" :label="item.menuName" :value="item.menuId"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="menuType === 'C'" label="应用地址" prop="menuUrl">
          <el-input v-model="form.menuUrl" placeholder="请输入应用地址" />
        </el-form-item>
        <el-form-item prop="activeTimeout" label-width="auto">
          <template #label>
            <span>
              <el-tooltip content="指定时间无操作则过期（单位：秒），默认30分钟（1800秒）" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
              Token活跃超时时间
            </span>
          </template>
          <el-input v-model="form.activeTimeout" placeholder="请输入Token活跃超时时间" />
        </el-form-item>
        <el-form-item prop="timeout" label-width="auto">
          <template #label>
            <span>
              <el-tooltip content="指定时间必定过期（单位：秒），默认七天（604800秒）" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
              Token固定超时时间
            </span>
          </template>
          <el-input v-model="form.timeout" placeholder="请输入Token固定超时时间" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio v-for="dict in sys_normal_disable" :key="dict.value" :value="dict.value">
              {{ dict.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item prop="appIpList">
          <template #label>
            <span>
              <el-tooltip content="多个IP用英文逗号隔开" placement="top">
                <el-icon><question-filled /></el-icon>
              </el-tooltip>
              应用IP列表
            </span>
          </template>
          <el-input v-model="form.appIpList" type="textarea" placeholder="请输入应用IP" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
    <el-dialog v-model="bindingDialog.visible" :title="bindingDialog.title" width="500px" append-to-body>
      <el-form ref="bindingFormRef" :model="bindingForm" :rules="bindingRules" label-width="100px">
        <el-form-item label="关联应用keyId" prop="appKeyId" label-width="auto">
          <el-select v-model="bindingForm.appKeyId" placeholder="请选择关联应用keyId">
            <el-option v-for="item in keyInfoList" :key="item.id" :label="item.keyName" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="关联平台keyId" prop="platformKeyId" label-width="auto">
          <el-select v-model="bindingForm.platformKeyId" placeholder="请选择关联平台keyId">
            <el-option v-for="item in keyInfoList" :key="item.id" :label="item.keyName" :value="item.id"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button :loading="buttonLoading" type="primary" @click="bindingSubmitForm">确 定</el-button>
          <el-button @click="bindingCancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Client" lang="ts">
import {
  listClient,
  getClient,
  delClient,
  addClient,
  updateClient,
  changeStatus,
  generateClientSecretApi,
  setKeysApi,
  listAppMenusApi
} from '@/api/system/client';
import { ClientVO, ClientQuery, ClientForm, BindingForm } from '@/api/system/client/types';
import { listKeyInfoApi } from '@/api/system/key';
import { TEMPLATE_NAMES } from '@/views/auth';
import { useRoute } from 'vue-router';

const route = useRoute();

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_normal_disable } = toRefs<any>(proxy?.useDict('sys_normal_disable'));
const { sys_grant_type } = toRefs<any>(proxy?.useDict('sys_grant_type'));
const { sys_device_type } = toRefs<any>(proxy?.useDict('sys_device_type'));

const clientList = ref<ClientVO[]>([]);
const buttonLoading = ref(false);
const loading = ref(true);
const showSearch = ref(true);
const ids = ref<Array<string | number>>([]);
const single = ref(true);
const multiple = ref(true);
const total = ref(0);

const keyInfoList = ref<any[]>([]);

const queryFormRef = ref<ElFormInstance>();
const clientFormRef = ref<ElFormInstance>();
const bindingFormRef = ref<ElFormInstance>();
const bindingRules = {
  appKeyId: [{ required: true, message: '关联应用keyId不能为空', trigger: 'blur' }],
  platformKeyId: [{ required: true, message: '关联平台keyId不能为空', trigger: 'blur' }]
};

const dialog = reactive<DialogOption>({
  visible: false,
  title: ''
});

const bindingDialog = reactive<DialogOption>({
  visible: false,
  title: '关联keyId'
});

const bindingForm = ref<BindingForm>({
  id: undefined,
  appKeyId: undefined,
  platformKeyId: undefined,
  clientId: undefined
});

const menuType = ref('N');
const appList = ref();

// 布局配置（layoutConfig JSON 字符串）相关状态
const templateNames = TEMPLATE_NAMES;
const layoutConfigPreset = ref<string>('');
const layoutConfigJsonError = ref<string>('');
const layoutConfigOk = computed(() => !layoutConfigJsonError.value && (form.value.layoutConfig ?? '') !== '');
const parsedTemplate = computed<string>(() => {
  const raw = form.value.layoutConfig;
  if (!raw) return '';
  try {
    const obj = JSON.parse(raw);
    return typeof obj?.template === 'string' ? obj.template : '';
  } catch {
    return '';
  }
});
/** 模板对应的 JSON 预设骨架（不含业务字段，供"插入默认配置"使用） */
const presetSkeleton = (name: string) => ({
  version: 1,
  template: name,
  theme: { primaryColor: '#547bd6' },
  login: { title: '', subtitle: '', showRememberMe: true, showRegister: true, showWechat: false },
  register: { title: '', platformTitle: '', roles: [{ label: '法人', value: '法人', default: true }, { label: '安全责任人', value: '安全责任人' }], submitText: '注册' },
  resetPwd: { title: '', mode: 'steps' as const, submitText: '提交' }
});

const initFormData: ClientForm = {
  id: undefined,
  clientId: undefined,
  clientKey: undefined,
  clientSecret: undefined,
  grantTypeList: undefined,
  deviceType: undefined,
  activeTimeout: undefined,
  timeout: undefined,
  status: '0', // 默认勾选「正常」
  allowUrl: '*',
  menuUrl: '',
  menuId: undefined,
  appKeyId: undefined,
  platformKeyId: undefined,
  appIpList: undefined,
  layoutConfig: undefined,
  registerEnabled: '0', // 默认关闭（与后端 sys_client.register_enabled 默认值一致）
  resetPwdEnabled: '1' // 默认开启（与后端 sys_client.reset_pwd_enabled 默认值一致）
};
const data = reactive<PageData<ClientForm, ClientQuery>>({
  form: { ...initFormData },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    clientId: undefined,
    clientKey: undefined,
    clientSecret: undefined,
    grantType: undefined,
    deviceType: undefined,
    activeTimeout: undefined,
    timeout: undefined,
    status: undefined
  },
  rules: {
    id: [{ required: true, message: 'id不能为空', trigger: 'blur' }],
    clientId: [{ required: true, message: '客户端id不能为空', trigger: 'blur' }],
    clientKey: [{ required: true, message: '客户端名称不能为空', trigger: 'blur' }],
    clientSecret: [{ required: true, message: '客户端秘钥不能为空', trigger: 'blur' }],
    grantTypeList: [{ required: true, message: '授权类型不能为空', trigger: 'change' }],
    deviceType: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
    allowUrl: [{ required: true, message: '允许授权地址不能为空', trigger: 'blue' }],
    menuId: [{ required: true, message: '应用绑定类型不能为空', trigger: 'change' }],
    menuUrl: [{ required: true, message: '应用路径不能为空', trigger: 'blue' }],
    appKeyId: [{ required: true, message: '应用绑定类型不能为空', trigger: 'change' }],
    platformKeyId: [{ required: true, message: '平台绑定类型不能为空', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

/** 查询客户端管理列表 */
const getList = async () => {
  loading.value = true;
  const res = await listClient(queryParams.value);
  clientList.value = res.rows;
  total.value = res.total;
  loading.value = false;
};

const getAppList = async () => {
  const res = await listAppMenusApi();
  appList.value = res.data;
};

/** 取消按钮 */
const cancel = () => {
  reset();
  dialog.visible = false;
};

const bindingCancel = () => {
  bindingReset();
  bindingDialog.visible = false;
};

const bindingReset = () => {
  bindingForm.value = {
    id: undefined,
    appKeyId: undefined,
    platformKeyId: undefined,
    clientId: undefined
  };
};

const bindingSubmitForm = async () => {
  bindingFormRef.value?.validate(async (valid) => {
    if (valid) {
      buttonLoading.value = true;
      try {
        const res = await setKeysApi(bindingForm.value);
        console.log('res', res);
        proxy?.$modal.msgSuccess('关联成功');
        bindingCancel();
        getList();
      } catch (error) {
        console.log(error);
      } finally {
        buttonLoading.value = false;
      }
    }
  });
};

/** 表单重置 */
const reset = () => {
  form.value = { ...initFormData };
  menuType.value = 'N';
  layoutConfigPreset.value = '';
  layoutConfigJsonError.value = '';
  clientFormRef.value?.resetFields();
};

/** 搜索按钮操作 */
const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

/** 重置按钮操作 */
const resetQuery = () => {
  queryFormRef.value?.resetFields();
  handleQuery();
};

/** 多选框选中数据 */
const handleSelectionChange = (selection: ClientVO[]) => {
  ids.value = selection.map((item) => item.id);
  single.value = selection.length != 1;
  multiple.value = !selection.length;
};

/** 新增按钮操作 */
const handleAdd = () => {
  reset();
  getAppList();
  dialog.visible = true;
  dialog.title = '添加客户端管理';
};

/** 修改按钮操作 */
const handleUpdate = async (row?: ClientVO) => {
  reset();
  getAppList();
  const _id = row?.id || ids.value[0];
  const res = await getClient(_id);
  Object.assign(form.value, res.data);
  if (form.value.menuId) {
    menuType.value = 'H';
  }
  // 同步布局配置预设下拉；打开弹窗时把 JSON 格式化为 2 空格缩进，便于阅读修改
  if (form.value.layoutConfig) {
    try {
      form.value.layoutConfig = JSON.stringify(JSON.parse(form.value.layoutConfig), null, 2);
    } catch (e) {
      // 存量数据解析失败时保留原文，交由 validateLayoutConfig 提示错误
    }
    layoutConfigPreset.value = parsedTemplate.value || '';
  }
  validateLayoutConfig();
  dialog.visible = true;
  dialog.title = '修改客户端管理';
};

/** 提交按钮 */
const submitForm = () => {
  clientFormRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      // 布局配置：空字符串归一为 undefined（让后端保留旧值），非空必须通过 JSON 校验
      const raw = (form.value.layoutConfig ?? '').trim();
      if (raw) {
        if (!validateLayoutConfig()) {
          proxy?.$modal.msgError(`布局配置 JSON 格式错误：${layoutConfigJsonError.value}`);
          return;
        }
        form.value.layoutConfig = JSON.stringify(JSON.parse(raw), null, 0); // 压缩存储，去除首尾空白
      } else {
        form.value.layoutConfig = undefined;
      }

      buttonLoading.value = true;
      if (menuType.value == 'H') {
        form.value.menuUrl = undefined;
      }
      if (menuType.value == 'C') {
        form.value.menuId = undefined;
      }
      if (menuType.value == 'N') {
        form.value.menuId = undefined;
        form.value.menuUrl = undefined;
      }
      if (form.value.id) {
        await updateClient(form.value).finally(() => (buttonLoading.value = false));
      } else {
        await addClient(form.value).finally(() => (buttonLoading.value = false));
      }
      proxy?.$modal.msgSuccess('修改成功');
      dialog.visible = false;
      await getList();
    }
  });
};

const handleBinding = async (row?: ClientVO) => {
  getKeyList(row?.clientId);
  bindingDialog.visible = true;
  bindingForm.value.id = row?.id;
  bindingForm.value.appKeyId = row?.appKeyId || undefined;
  bindingForm.value.platformKeyId = row?.platformKeyId || undefined;
  bindingForm.value.clientId = row?.clientId || undefined;
};

/** 删除按钮操作 */
const handleDelete = async (row?: ClientVO) => {
  const _ids = row?.id || ids.value;
  await proxy?.$modal.confirm('是否确认删除客户端管理编号为"' + _ids + '"的数据项？').finally(() => (loading.value = false));
  await delClient(_ids);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

/** 导出按钮操作 */
const handleExport = () => {
  proxy?.download(
    'system/client/export',
    {
      ...queryParams.value
    },
    `client_${new Date().getTime()}.xlsx`
  );
};

/** 状态修改  */
const handleStatusChange = async (row: ClientVO) => {
  const text = row.status === '0' ? '启用' : '停用';
  try {
    await proxy?.$modal.confirm('确认要"' + text + '"吗?');
    await changeStatus(row.clientId, row.status);
    proxy?.$modal.msgSuccess(text + '成功');
  } catch (err) {
    row.status = row.status === '0' ? '1' : '0';
  }
};

/**
 * 生成客户端秘钥
 */
const generateClientSecret = async () => {
  const data = await generateClientSecretApi();
  form.value.clientSecret = data.data;
};

const getKeyList = async (clientId: string) => {
  const res = await listKeyInfoApi({ keyType: 'PEK', clientId });
  keyInfoList.value = res.data;
};

/**
 * 校验 layoutConfig JSON 格式，错误信息写入 layoutConfigJsonError
 */
const validateLayoutConfig = () => {
  const raw = (form.value.layoutConfig ?? '').trim();
  if (!raw) {
    layoutConfigJsonError.value = '';
    return true;
  }
  try {
    JSON.parse(raw);
    layoutConfigJsonError.value = '';
    return true;
  } catch (e: any) {
    layoutConfigJsonError.value = e?.message || 'JSON 解析失败';
    return false;
  }
};

/** 模板下拉变化：仅记录选择，不直接改 JSON（避免误覆盖已有配置） */
const onPresetChange = (_v: string | number | undefined) => {
  // no-op（保留"插入默认配置"显式触发，覆盖前用户可先清空或备份）
};

/** 覆盖当前 JSON 为所选模板的默认骨架 */
const applyPreset = () => {
  if (!layoutConfigPreset.value) return;
  form.value.layoutConfig = JSON.stringify(presetSkeleton(layoutConfigPreset.value), null, 2);
  validateLayoutConfig();
};

/** 格式化布局配置 JSON（2 空格缩进）；解析失败时提示并保留原文 */
const formatLayoutConfig = () => {
  const raw = (form.value.layoutConfig ?? '').trim();
  if (!raw) return;
  try {
    form.value.layoutConfig = JSON.stringify(JSON.parse(raw), null, 2);
    layoutConfigJsonError.value = '';
  } catch (e: any) {
    layoutConfigJsonError.value = e?.message || 'JSON 解析失败';
    proxy?.$modal.msgError('JSON 格式错误，无法格式化：' + layoutConfigJsonError.value);
  }
};

/** 仅设置 template 字段（保留已有其它字段） */
const setTemplateOnly = () => {
  const target = layoutConfigPreset.value;
  if (!target) return;
  const raw = (form.value.layoutConfig ?? '').trim();
  // 选 default 模板：默认样式走前端硬编码，不需要任何配置
  if (target === 'default') {
    form.value.layoutConfig = undefined;
    layoutConfigJsonError.value = '';
    proxy?.$modal.msgSuccess('已切换到 default 模板（前端走硬编码默认样式，布局配置已清空）');
    return;
  }
  // 选非 default 模板
  if (!raw) {
    // 当前为空时退化为插入骨架（这样就有了非 default 模板的最小可用配置）
    applyPreset();
    proxy?.$modal.msgSuccess(`已切换到 ${target} 模板（自动写入默认配置骨架）`);
    return;
  }
  let obj: any;
  try {
    obj = JSON.parse(raw);
  } catch {
    proxy?.$modal.msgError('当前 JSON 格式错误，无法仅设置 template 字段');
    return;
  }
  if (obj.template === target) {
    proxy?.$modal.msgWarning(`template 字段已是 ${target}，无需变更`);
    return;
  }
  obj.template = target;
  form.value.layoutConfig = JSON.stringify(obj, null, 2);
  validateLayoutConfig();
  proxy?.$modal.msgSuccess(`template 字段已更新为 ${target}，其它配置已保留`);
};

onMounted(async () => {
  await getList();
  // 从应用菜单管理页携带 clientId 跳转过来时，自动定位到该客户端并打开编辑
  const targetClientId = route.query.clientId as string | undefined;
  if (targetClientId) {
    const target = clientList.value.find((c) => c.clientId === targetClientId);
    if (target) {
      handleUpdate(target);
    } else {
      proxy?.$modal.msgWarning(
        `未找到 clientId=${targetClientId} 的客户端，可能已被停用或删除`
      );
    }
  }
});
</script>

<style lang="scss" scoped>
.layout-config-editor {
  width: 100%;
}
.layout-config-hint {
  margin-bottom: 8px;
}
.layout-config-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.layout-config-label {
  color: #606266;
  font-size: 13px;
}
.layout-config-status {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
  &.is-ok {
    color: #67c23a;
  }
  &.is-err {
    color: #f56c6c;
  }
}
</style>
