<!--
 * @Author: mjzhu
 * @Date: 2022-06-08 11:38:30
 * @LastEditTime: 2023-03-15 17:31:27
 * @FilePath: \ddh-ui\src\pages\securityCenter\commponents\addUser.vue
-->
<template>
  <div style="padding-top: 10px">
    <a-form :label-col="labelCol" :wrapper-col="wrapperCol" :form="form" class="p0-32-10-32 form-content">
      <a-form-item label="标签名称">
        <a-input v-decorator="[
            'nodeLabel',
            { rules: [{ required: true, message: '标签名称不能为空!' }] },
          ]" placeholder="请输入标签名称" />
      </a-form-item>
    </a-form>
    <div class="ant-modal-confirm-btns-new">
      <a-button style="margin-right: 10px" type="primary" @click.stop="handleSubmit" :loading="loading">确认</a-button>
      <a-button @click.stop="formCancel">取消</a-button>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    detail: {
      type: Object,
      default: function () {
        return {};
      },
    },
    callBack: Function,
  },
  data() {
    return {
      clusterId: Number(localStorage.getItem("clusterId") || -1),
      editFlag: false,
      labelCol: {
        xs: { span: 24 },
        sm: { span: 6 },
      },
      wrapperCol: {
        xs: { span: 24 },
        sm: { span: 18 },
      },
      form: this.$form.createForm(this),
      loading: false,
      groupList:[],
    };
  },
  watch: {},
  methods: {
    checkName(rule, value, callback) {
      var reg = /[\u4E00-\u9FA5]|[\uFE30-\uFFA0]/g;
      if (reg.test(value)) {
        callback(new Error("名称中不能包含中文"));
      }
      if (/\s/g.test(value)) {
        callback(new Error("名称中不能包含空格"));
      }
      callback();
    },
    formCancel() {
      this.$destroyAll();
    },
    handleSubmit(e) {
      const _this = this;
      e.preventDefault();
      this.form.validateFields((err, values) => {
        if (!err) {
          const params = {
          nodeLabel: values.nodeLabel,
          clusterId: this.clusterId,
        };
        this.loading = true;
        const ajaxApi = global.API.saveLabel;
        this.$axiosPost(ajaxApi, params)
          .then((res) => {
            this.loading = false;
            if (res.code === 200) {
              this.$message.success("保存成功", 2);
              this.$destroyAll();
              _this.callBack();
            }
          })
        .catch((err) => {});
          }
      });
    },
  },
  mounted() {
  },
  created(){
  }
};
</script>
<style lang="less" scoped>
</style>
