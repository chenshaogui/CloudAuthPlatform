<template>
  <div class="p-2">
    <transition :enter-active-class="proxy?.animate.searchAnimate.enter" :leave-active-class="proxy?.animate.searchAnimate.leave">
      <div v-show="showSearch" class="mb-[10px]">
        <el-card shadow="hover">
          <el-form ref="queryFormRef" :model="queryParams" :inline="true">
            <el-form-item label="客户端" prop="clientId">
              <el-select v-model="queryParams.clientId" placeholder="请选择应用名称" filterable clearable @change="handleQuery">
                <el-option v-for="item in clientList" :key="item.clientId" :label="item.clientKey" :value="item.clientId"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="密钥名称" prop="keyName">
              <el-input v-model="queryParams.keyName" placeholder="请输入密钥名称" clearable @keyup.enter="handleQuery" />
            </el-form-item>
            <el-form-item label="密钥类型" prop="keyType">
              <el-select v-model="queryParams.keyType" placeholder="请选择密钥类型" clearable @change="handleQuery">
                <el-option v-for="item in keyTypeOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
              </el-select>
            </el-form-item>
            <el-form-item label="密钥算法" prop="keyAlthm">
              <el-select v-model="queryParams.keyAlthm" placeholder="请选择密钥算法" clearable @change="handleQuery">
                <el-option v-for="item in keyAlthmOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
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
        <el-row :gutter="10" class="mb8">
          <el-col :span="1.5">
            <el-button v-hasPermi="['system:key:add']" type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
          </el-col>
          <right-toolbar v-model:show-search="showSearch" @query-table="getList"></right-toolbar>
        </el-row>
      </template>

      <el-table v-loading="loading" border :data="keyList">
        <el-table-column label="客户端ID" align="center" prop="clientId" :show-overflow-tooltip="true" />
        <el-table-column label="客户端名称" align="center" prop="clientName" :show-overflow-tooltip="true" />
        <el-table-column label="密钥名称" align="center" prop="keyName" :show-overflow-tooltip="true" />
        <el-table-column label="密钥类型" align="center" prop="keyType" />
        <el-table-column label="密钥算法" align="center" prop="keyAlthm" :show-overflow-tooltip="true" />
        <el-table-column label="密钥过期时间" align="center" prop="expireTime" :show-overflow-tooltip="true" />
        <el-table-column label="密钥轮换次数" align="center" prop="rotateCount" />
        <el-table-column label="密钥最后轮换时间" align="center" prop="lastRotateTime" :show-overflow-tooltip="true" />
        <el-table-column label="状态" align="center" prop="status">
          <template #default="{ row }">
            <dict-tag :options="sys_notice_status" :value="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150" class-name="small-padding fixed-width">
          <template #default="scope">
            <el-tooltip content="查看" placement="top">
              <el-button v-hasPermi="['system:key:view']" link type="primary" icon="View" @click="handleDetails(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="密钥轮换" placement="top">
              <el-button v-hasPermi="['system:key:rotation']" link type="primary" icon="Refresh" @click="handleRotation(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="密钥轮换记录" placement="top">
              <el-button
                v-hasPermi="['system:key:rotationRecords']"
                link
                type="primary"
                icon="List"
                @click="handleRotationList(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="密钥销毁" placement="top">
              <el-button v-hasPermi="['system:key:destroy']" link type="primary" icon="Remove" @click="handleDistroy(scope.row)"></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <pagination v-show="total > 0" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
    </el-card>
    <!-- 添加密钥信息 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" :width="dialogWidth || '650px'" append-to-body>
      <template v-if="dialogMode === 'ADD'">
        <el-form ref="keyFormRef" :model="form" :rules="rules" label-width="80px">
          <el-row>
            <el-col :span="24">
              <el-form-item label="客户端" prop="clientId">
                <el-select v-model="form.clientId" placeholder="请选择客户端">
                  <el-option v-for="item in clientList" :key="item.clientId" :label="item.clientKey" :value="item.clientId"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="密钥类型" prop="keyType">
                <el-select v-model="form.keyType" placeholder="请选择">
                  <el-option v-for="item in keyTypeOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="密钥算法" prop="keyAlthm">
                <el-select v-model="form.keyAlthm" placeholder="请选择">
                  <el-option v-for="item in keyAlthmOptions" :key="item.value" :label="item.label" :value="item.value"></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>
      <template v-else-if="dialogMode === 'DETAILS'">
        <el-descriptions title="密钥信息" border :column="1">
          <el-descriptions-item label="应用id：" v-if="details.clientId">{{ details.clientId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用名称：" v-if="details.clientName">{{ details.clientName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥名称：" v-if="details.keyName">{{ details.keyName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥类型：" v-if="details.keyType">{{ details.keyType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥标识：" v-if="details.keyId">{{ details.keyId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="明文：" min-width="130px" v-if="details.unkeys">
            <template #label> 明文： </template>
            <template #default>
              <div style="width: 440px">{{ details.unkeys || '-' }}</div>
            </template>
          </el-descriptions-item>
          <el-descriptions-item label="密钥算法：" v-if="details.keyAlthm">{{ details.keyAlthm || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥过期时间：" v-if="details.expireTime">{{ details.expireTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥最后轮换时间：" v-if="details.lastRotateTime">{{ details.lastRotateTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="密钥轮换次数：" v-if="details.rotateCount">{{ details.rotateCount || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态：" v-if="details.status">
            <dict-tag style="display: inline-block" :options="sys_notice_status" :value="details.status" />
          </el-descriptions-item>
        </el-descriptions>
      </template>
      <template v-else-if="dialogMode === 'ROTATION'">
        <el-table v-loading="loading" border :data="rotationList">
          <el-table-column label="ID" align="center" prop="id" :show-overflow-tooltip="true" />
          <el-table-column label="轮换类型" align="center" prop="rotationType" />
          <el-table-column label="原密钥ID" align="center" prop="oldKeyId" :show-overflow-tooltip="true" />
          <el-table-column label="新密钥ID" align="center" prop="newKeyId" :show-overflow-tooltip="true" />
          <el-table-column label="轮换触发条件" align="center" prop="rotationTrigger" />
          <el-table-column label="同步失败原因" align="center" prop="syncFailedReason" />
          <el-table-column label="回滚状态" align="center" prop="rollbackStatus" :formatter="formatRollbackStatus" />
          <el-table-column label="轮换执行时间" align="center" prop="rotationTime" :show-overflow-tooltip="true" />
          <el-table-column label="同步状态" align="center" prop="syncStatus" :formatter="formatSyncStatus" />
          <el-table-column label="状态" align="center">
            <template #default="{ row }">
              <dict-tag :options="sys_notice_status" :value="row.status" />
            </template>
          </el-table-column>
        </el-table>
        <pagination
          v-show="rotationTotal > 0"
          v-model:page="rotationParams.pageNum"
          v-model:limit="rotationParams.pageSize"
          :total="rotationTotal"
          @pagination="getRotationList"
        />
      </template>
      <template #footer>
        <div class="dialog-footer" v-if="dialogMode === 'ADD'">
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Key" lang="ts">
import { listKeyApi, addKeyInfoApi, rotationKeyApi, distroyKeyApi, listRotationKeyApi, getKeyInfoApi } from '@/api/system/key';
import { listClient } from '@/api/system/client';
import { KeyQuery, KeyForm, KeyVo } from '@/api/system/key/types';
import { ClientVO } from '@/api/system/client/types';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;
const { sys_notice_status } = toRefs<any>(proxy?.useDict('sys_notice_status'));
const showSearch = ref(true);
const loading = ref(false);
const submitLoading = ref(false);
const keyList = ref<KeyVo[]>([]);
const total = ref(0);
const dialogMode = ref('ADD');
const details = ref<any>({});
const rotationList = ref<KeyVo[]>([]);
const rotationTotal = ref(0);
const dialogWidth = ref('650px');
const clientList = ref<ClientVO[]>([]);

interface rotationQuery {
  pageNum: number;
  pageSize: number;
  id: string;
}

const rotationParams = ref<rotationQuery>({
  pageNum: 1,
  pageSize: 10,
  id: ''
});

const initKeyForm: keyForm = {
  clientId: '',
  keyType: '',
  keyAlthm: ''
};

const keyTypeOptions = [
  {
    label: 'TEK',
    value: 'TEK'
  },
  {
    label: 'DEK',
    value: 'DEK'
  },
  {
    label: 'AEK',
    value: 'AEK'
  },
  {
    label: 'PEK',
    value: 'PEK'
  }
];

// 密钥算法列表
const keyAlthmOptions = [
  // {
  //   label: 'RSA',
  //   value: 'RSA'
  // },
  // {
  //   label: 'AES',
  //   value: 'AES'
  // },
  {
    label: 'SM2',
    value: 'SM2'
  },
  {
    label: 'SM4',
    value: 'SM4'
  }
];

const data = reactive<PageData<KeyForm, KeyQuery>>({
  form: { ...initKeyForm },
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    clientId: '',
    keyName: '',
    keyType: '',
    keyAlthm: ''
  },
  rules: {
    clientId: [{ required: true, message: '请选择应用', trigger: 'change' }],
    keyName: [{ required: true, message: '请输入密钥名称', trigger: 'blur' }],
    keyType: [{ required: true, message: '请选择密钥类型', trigger: 'change' }],
    keyAlthm: [{ required: true, message: '请选择密钥算法', trigger: 'change' }]
  }
});

const { queryParams, form, rules } = toRefs(data);

const dialog = reactive(<DialogOption>{
  visible: false,
  title: ''
});
const keyFormRef = ref<ElFormInstance>();

const getList = () => {
  listKeyApi(queryParams.value).then((response) => {
    keyList.value = response.rows;
    total.value = response.total;
  });
};

const getRotationList = async () => {
  const res = await listRotationKeyApi({ ...rotationParams.value });
  rotationList.value = res.rows;
  rotationTotal.value = res.total;
};

const handleQuery = () => {
  queryParams.value.pageNum = 1;
  getList();
};

const getClientList = async () => {
  const res = await listClient();
  clientList.value = res.rows;
};

const resetQuery = () => {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    clientId: '',
    keyName: '',
    keyType: '',
    keyAlthm: ''
  };
  handleQuery();
};

const handleAdd = () => {
  dialogMode.value = 'ADD';
  dialogWidth.value = '650px';
  reset();
  dialog.visible = true;
  dialog.title = '添加密钥';
};

const reset = () => {
  form.value = { ...initKeyForm };
};

const cancel = () => {
  reset();
  dialog.visible = false;
};

const submitForm = () => {
  keyFormRef.value?.validate(async (valid) => {
    if (valid) {
      try {
        submitLoading.value = true;
        const res = await addKeyInfoApi({ ...form.value });
        if (res.code === 200) {
          proxy?.$modal.msgSuccess('操作成功');
          cancel();
          await handleQuery();
        }
      } catch (error) {
        console.log(error);
      } finally {
        submitLoading.value = false;
      }
    }
  });
};

const handleDetails = async (row: KeyVo) => {
  dialogMode.value = 'DETAILS';
  dialogWidth.value = '650px';
  dialog.title = '';
  const res = await getKeyInfoApi(row?.id);
  details.value = res.data;
  dialog.visible = true;
};

const handleRotation = async (row: KeyVo) => {
  await proxy?.$modal.confirm('是否确认密钥轮换？');
  const res = await rotationKeyApi(row.id);
  if (res.code === 200) {
    proxy?.$modal.msgSuccess('操作成功');
    await getList();
  }
};

const handleRotationList = async (row: KeyVo) => {
  rotationParams.value.id = row.id;
  rotationList.value = [];
  rotationTotal.value = 0;
  dialogWidth.value = '80%';
  dialog.visible = true;
  dialog.title = '密钥轮换列表';
  dialogMode.value = 'ROTATION';
  getRotationList();
};

const handleDistroy = async (row: KeyVo) => {
  await proxy?.$modal.confirm('是否确认密钥销毁？');
  const res = await distroyKeyApi(row.id);
  if (res.code === 200) {
    proxy?.$modal.msgSuccess('操作成功');
    await getList();
  }
};

const formatRollbackStatus = (row: any) => {
  // 0 - 未回滚、1 - 回滚中、2 - 回滚成功、3 - 回滚失败
  const statusMap = {
    '0': '正常',
    '1': '回滚中',
    '2': '回滚成功',
    '3': '回滚失败'
  };
  return statusMap[row.rollbackStatus];
};

const formatSyncStatus = (row: any) => {
  // 0 - 未同步1 - 同步中2 - 同步成功3 - 同步失败
  const statusMap = {
    '0': '未同步',
    '1': '同步中',
    '2': '同步成功',
    '3': '同步失败'
  };
  return statusMap[row.syncStatus];
};

onMounted(() => {
  getList();
  getClientList();
});
</script>
