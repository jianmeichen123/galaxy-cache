package com.galaxyinternet.framework.cache;

public enum CacheModule {
	REGISTER {
		@Override
		public String getName() {
			return "注册";
		}
	},
	LOGIN {
		@Override
		public String getName() {
			return "登录";
		}
	},
	PERMISSION {
		@Override
		public String getName() {
			return "权限";
		}
	},
	ROLE {
		@Override
		public String getName() {
			return "角色";
		}
	},
	DEPT {
		@Override
		public String getName() {
			return "部门";
		}
	};
	public abstract String getName();

	public int getValue() {
		return ordinal();
	}
}
