<template>
  <div class="app-stage">
    <header class="masthead">
      <div class="masthead-inner">
        <span class="wordmark">VidLore</span>

        <div class="masthead-tools">
          <div
              v-if="uploading || isOffline || activeTasks.length"
              class="status-line"
              :class="{ 'is-active': uploading || activeTasks.length, 'is-offline': isOffline }"
              role="status"
              aria-live="polite"
          >
            <span class="status-dot" aria-hidden="true"></span>
            <span class="status-text">{{ systemStatusText }}</span>
          </div>

          <template v-if="!currentUser">
            <button class="ghost-btn" @click="authMode = 'login'; openAuthModal()">登录</button>
            <button class="auth-btn" @click="authMode = 'register'; openAuthModal()">注册</button>
          </template>

          <div v-else class="user-profile">
            <span class="user-avatar" aria-hidden="true">{{ (currentUser.nickname || '?').slice(0, 1) }}</span>
            <span class="user-name">{{ currentUser.nickname }}</span>
            <button class="logout-btn" @click="logout" title="退出登录" aria-label="退出登录">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
            </button>
          </div>
        </div>
      </div>
    </header>

    <main class="main-container" :class="{ 'is-app': currentUser }">
      <section class="hero" :class="{ 'is-compact': currentUser }">
        <template v-if="!currentUser">
          <h1 class="hero-title"><span>两小时的网课，</span><span>五分钟抓住重点</span></h1>
          <p class="hero-lede">贴个链接，自动帮你划重点。哪里没看懂，点时间戳直接跳回原片。</p>
        </template>

        <input
            type="file"
            id="file-input"
            @change="handleFileChange"
            accept=".mp4,.mov,.mkv,.avi,.webm,.m4v"
            hidden
        />

        <div
            class="composer"
            :class="{ 'is-compact': currentUser, 'processing': uploading, 'is-dragover': isDragOver }"
            @dragenter.prevent="handleDragEnter"
            @dragover.prevent="isDragOver = true"
            @dragleave.prevent="handleDragLeave"
            @drop.prevent="handleDrop"
        >
          <template v-if="!uploading">
            <div class="composer-link" @click.stop>
              <svg class="composer-link-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"></path><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"></path></svg>
              <input
                  id="video-url"
                  v-model="videoUrl"
                  type="text"
                  inputmode="url"
                  autocomplete="off"
                  spellcheck="false"
                  placeholder="粘贴视频链接"
                  aria-label="视频链接"
                  :disabled="uploading"
                  @keyup.enter="handleUrlUpload"
              />
              <button class="composer-go" :disabled="uploading || !videoUrl.trim()" @click="handleUrlUpload">导入</button>
            </div>

            <div class="composer-or" aria-hidden="true">或</div>

            <label for="file-input" class="composer-file">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
              <span class="composer-file-title">{{ isDragOver ? '松手开始上传' : '上传本地视频' }}</span>
            </label>
          </template>

          <div class="composer-busy" v-else>
            <div class="composer-busy-head">
              <div class="quantum-loader"></div>
              <div class="composer-busy-text">
                <span class="busy-text">{{ uploadProgress.label }}</span>
                <span v-if="uploadProgress.filename" class="busy-file">{{ uploadProgress.filename }}</span>
              </div>
              <button v-if="uploadAbort" type="button" class="busy-cancel" @click="cancelUpload">取消上传</button>
            </div>
            <div
                v-if="uploadProgress.percent !== null"
                class="upload-progress"
                role="progressbar"
                aria-label="视频上传进度"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="uploadProgress.percent"
            >
              <span :style="{ width: `${uploadProgress.percent}%` }"></span>
            </div>
            <span v-if="uploadProgress.detail" class="busy-stat" aria-live="polite">{{ uploadProgress.detail }}</span>
            <span v-if="uploadProgress.warning" class="busy-warning" role="status">{{ uploadProgress.warning }}</span>
          </div>
        </div>

        <ul v-if="!currentUser" class="source-list" aria-label="支持的视频来源">
          <li>B 站</li>
          <li>YouTube</li>
          <li>抖音</li>
          <li>本地 MP4 / MOV / MKV</li>
        </ul>

        <div v-if="resumableFile && !uploading" class="upload-resume" role="status">
          <span>{{ resumeHint }}</span>
          <div class="upload-resume-actions">
            <button type="button" class="is-primary" @click="resumeUpload">继续上传</button>
            <button type="button" @click="discardResumableUpload">重新开始</button>
          </div>
        </div>

        <transition name="toast-pop">
          <div
              v-if="message"
              class="notification-bar"
              :class="{ 'error': messageIsError }"
              :role="messageIsError ? 'alert' : 'status'"
              :aria-live="messageIsError ? 'assertive' : 'polite'"
              :title="messageIsError ? '点击关闭这条提示' : null"
              @click="dismissMessage"
          >
            {{ message }}
          </div>
        </transition>
      </section>

      <section v-if="!currentUser" class="showcase" aria-label="分析结果示例">
        <div class="showcase-frame">
          <div class="showcase-video">
            <div class="showcase-slide" aria-hidden="true">
              <span class="slide-title">二叉树的遍历</span>
              <svg class="slide-tree" viewBox="0 0 220 120" fill="none">
                <g stroke="#8fa0c4" stroke-width="2"><line x1="110" y1="22" x2="60" y2="62"/><line x1="110" y1="22" x2="160" y2="62"/><line x1="60" y1="62" x2="32" y2="102"/><line x1="60" y1="62" x2="88" y2="102"/><line x1="160" y1="62" x2="188" y2="102"/></g>
                <g fill="#18202e" stroke="#dfe6f5" stroke-width="2"><circle cx="110" cy="22" r="13"/><circle cx="60" cy="62" r="13"/><circle cx="160" cy="62" r="13"/><circle cx="32" cy="102" r="13"/><circle cx="88" cy="102" r="13"/><circle cx="188" cy="102" r="13"/></g>
                <g fill="#dfe6f5" font-size="12" font-family="sans-serif" text-anchor="middle"><text x="110" y="26">8</text><text x="60" y="66">3</text><text x="160" y="66">10</text><text x="32" y="106">1</text><text x="88" y="106">6</text><text x="188" y="106">14</text></g>
              </svg>
              <span class="slide-caption">中序：左 → 根 → 右　得到 1 3 6 8 10 14</span>
            </div>
            <div class="showcase-scrubber" aria-hidden="true">
              <span class="scrubber-fill" style="width: 19.4%"></span>
              <i style="left: 4.6%"></i>
              <i class="is-current" style="left: 19.4%"></i>
              <i style="left: 32.2%"></i>
              <span class="scrubber-time">08:42 / 45:00</span>
            </div>
          </div>

          <div class="showcase-notes">
            <p class="showcase-goal">“帮我整理成复习笔记”</p>
            <h3>核心结论</h3>
            <ul>
              <li>前序遍历按“根、左、右”的顺序访问 <span class="cite-mark">02:05</span></li>
              <li class="is-current">中序遍历二叉搜索树，得到的就是有序序列 <span class="cite-mark">08:42</span></li>
              <li>迭代写法用一个栈代替递归调用 <span class="cite-mark">14:30</span></li>
            </ul>
            <h3>自测题</h3>
            <ul>
              <li>给出前序和中序序列，能唯一还原一棵二叉树吗？</li>
            </ul>
          </div>
        </div>
        <p class="showcase-caption">一节 45 分钟数据结构课的分析结果。黄色时间戳对应进度条上的位置，点哪条就跳到哪里。</p>
      </section>

      <section v-if="list.length > 0" class="workspace-section">
        <div class="section-header">
          <div class="library-title">
            <h2>我的视频</h2>
            <span class="count-chip">{{ list.length }}</span>
          </div>
          <label class="library-search">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            <input v-model="searchQuery" type="search" placeholder="搜索视频" aria-label="搜索视频名称" />
          </label>
        </div>

        <ul class="media-grid">
          <li v-for="item in visibleList" :key="item.id" class="media-card">
            <button
                class="media-cover"
                :class="[`tone-${item.id % 4}`, { 'has-frame': item.coverUrl || coverUrls[item.id] }]"
                :disabled="item.status !== 'COMPLETED'"
                :title="actionTitle(item, '打开 Video Agent')"
                @click="openAgent(item)"
            >
              <img v-if="item.coverUrl" class="cover-frame" :src="item.coverUrl" alt="" loading="lazy" />
              <video
                  v-else-if="coverUrls[item.id]"
                  class="cover-frame"
                  :src="coverUrls[item.id]"
                  preload="metadata"
                  muted
                  playsinline
                  disablepictureinpicture
                  tabindex="-1"
                  aria-hidden="true"
                  @error="coverUrls[item.id] = ''"
              ></video>
              <span class="cover-title">{{ displayName(item) }}</span>
              <span class="cover-cta" aria-hidden="true">打开分析</span>
            </button>

            <div class="media-body">
              <div class="meta-tags">
                <span
                    class="status-indicator"
                    :class="cardStatusClass(item)"
                    :title="cardStatusTitle(item)"
                >{{ cardStatusLabel(item) }}</span>
                <span class="time-tag">{{ formatTime(item.uploadTime) }}</span>
              </div>
              <div class="action-dock">
                <button
                    class="dock-item ai-core"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '打开 Video Agent')"
                    @click="openAgent(item)"
                >AI 分析</button>
                <button
                    class="dock-item"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '提取文字')"
                    @click="transcribe(item.id)"
                >文字稿</button>
                <button
                    class="dock-item"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '下载音频')"
                    @click="downloadAudio(item)"
                >下载音频</button>
                <button
                    class="delete-btn"
                    :disabled="deletingId === item.id"
                    :title="deletingId === item.id ? '正在删除…' : '删除视频'"
                    :aria-label="`删除 ${item.filename}`"
                    @click.stop="deleteItem(item)"
                >
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14H6L5 6"></path><path d="M8 6V4h8v2"></path></svg>
                </button>
              </div>
            </div>
          </li>
        </ul>
        <div v-if="visibleList.length === 0" class="library-empty">
          <p>没有名称包含“{{ searchQuery }}”的视频</p>
          <button type="button" @click="searchQuery = ''">清除搜索</button>
        </div>
      </section>

      <div v-if="currentUser && (listError || !list.length)" class="library-empty" role="status">
        <p>{{ listLoading ? '正在加载你的视频…' : listError || '还没有视频。在上面贴个链接，或者把视频拖进来。' }}</p>
        <button v-if="listError" type="button" :disabled="listLoading" @click="fetchList({ notify: true })">重新加载</button>
      </div>

      <div class="sidebar-backdrop" v-if="sidebar.visible" @click="closeSidebar"></div>
      <div
          ref="sidebarPanel"
          class="sidebar-panel"
          :class="{ 'is-open': sidebar.visible }"
          :inert="!sidebar.visible"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          :aria-label="sidebarHeading || '任务详情'"
          @keydown="trapPanelFocus($event, sidebarPanel)"
      >
        <div class="sidebar-header">
          <div class="sidebar-title">{{ sidebarHeading }}</div>
          <button class="close-btn" @click="closeSidebar" aria-label="关闭分析面板">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="6" y1="6" x2="18" y2="18"></line><line x1="18" y1="6" x2="6" y2="18"></line></svg>
          </button>
        </div>
        <div ref="sidebarBody" class="sidebar-body">
          <div v-if="sidebar.type === 'ai'" class="video-evidence">
            <video
                v-if="sidebar.playbackUrl"
                ref="videoPlayer"
                :src="sidebar.playbackUrl"
                controls
                playsinline
                preload="metadata"
                @error="handlePlaybackError"
            ></video>
            <div v-else-if="sidebar.playbackLoading" class="video-evidence-loading">正在载入原视频…</div>
            <div v-else-if="sidebar.playbackError" class="video-evidence-error" role="alert">
              <span>{{ sidebar.playbackError }}</span>
              <button type="button" @click="retryPlayback">重新加载</button>
            </div>
            <p v-if="sidebar.playbackUrl">点击结果里的黄色时间戳，视频会跳到对应画面。</p>
          </div>

          <div v-if="sidebar.type === 'ai' && sidebar.mode === 'compose'" class="agent-composer">
            <div class="composer-field">
              <p class="agent-caption">选择分析模式，它决定结果的结构</p>
              <div class="goal-presets agent-mode-row">
                <button
                    v-for="m in analysisModes"
                    :key="m.value"
                    :class="{ active: sidebar.analysisMode === m.value }"
                    :aria-pressed="sidebar.analysisMode === m.value"
                    @click="sidebar.analysisMode = m.value"
                >
                  <strong>{{ m.title }}</strong>
                  <span>{{ m.description }}</span>
                </button>
              </div>
            </div>

            <div class="composer-field">
              <p class="agent-caption">你想从这个视频得到什么</p>
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <textarea
                  v-model="sidebar.goal"
                  aria-label="分析目标"
                  maxlength="500"
                  placeholder="例如：梳理核心观点，给出带时间戳的证据和可执行建议（Ctrl / ⌘ + Enter 提交）"
                  @keydown.ctrl.enter.prevent="submitAgent"
                  @keydown.meta.enter.prevent="submitAgent"
              ></textarea>
              <p v-if="sidebar.goal.length > 400" class="field-counter">已输入 {{ sidebar.goal.length }} / 500 字</p>
              <div class="goal-presets preset-row">
                <button
                    v-for="preset in goalPresets"
                    :key="preset.title"
                    :class="{ active: sidebar.goal === preset.prompt }"
                    @click="sidebar.goal = preset.prompt"
                >
                  <strong>{{ preset.title }}</strong>
                  <span>{{ preset.description }}</span>
                </button>
              </div>
            </div>

            <button class="agent-run-btn" :disabled="!sidebar.goal.trim()" @click="submitAgent">
              {{ sidebar.error ? '重新分析' : '开始分析' }}
            </button>
          </div>

          <div v-else-if="sidebar.loading" class="agent-running">
            <div class="loading-state">
              <div class="quantum-loader small"></div>
              <p aria-live="polite">{{ loadingHeadline }}</p>
              <p v-if="sidebar.streamOffline" class="stream-offline" role="status">
                连接中断，正在第 {{ sidebar.streamRetry }} 次重连。任务仍在服务端继续。
              </p>
              <p class="loading-hint">可以关闭这个面板，任务会在后台继续，完成后会提醒你。</p>
            </div>
            <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
              <span class="meta-label">任务计划</span>
              <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
            </div>
            <div v-if="traceStages.length" class="agent-meta-block">
              <span class="meta-label">已完成的阶段</span>
              <ol class="stage-list">
                <li v-for="stage in traceStages" :key="stage[0]"><span>{{ stage[0] }}</span><span class="stage-time">{{ stage[1] }}</span></li>
              </ol>
            </div>
          </div>

          <div v-else>
            <div v-if="sidebar.type === 'ai'">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <div class="result-actions">
                <button type="button" @click="startNewAnalysis">换一个目标</button>
                <button type="button" :disabled="!sidebar.content" @click="copyResult">复制结果</button>
                <button type="button" :disabled="!sidebar.content" @click="downloadResult">导出 Markdown</button>
              </div>
              <div class="evidence-search">
                <div class="evidence-search-form">
                  <input
                      v-model="sidebar.evidenceQuery"
                      aria-label="视频证据检索"
                      maxlength="500"
                      placeholder="在视频里找某页 PPT、某句字幕、某段代码或讲解"
                      @keyup.enter="searchEvidence"
                  />
                  <button type="button" :disabled="sidebar.evidenceLoading || !sidebar.evidenceQuery.trim()" @click="searchEvidence">
                    {{ sidebar.evidenceLoading ? '查找中' : '查找片段' }}
                  </button>
                </div>
                <p v-if="sidebar.evidenceError" class="evidence-search-error" aria-live="polite">{{ sidebar.evidenceError }}</p>
                <div v-if="sidebar.evidenceResults.length" class="evidence-search-results" aria-live="polite">
                  <button
                      v-for="hit in sidebar.evidenceResults"
                      :key="`${hit.startMs}-${hit.endMs}`"
                      type="button"
                      :title="hit.snippet || '该时间段暂无可展示文本'"
                      @click="seekToEvidence(hit.startMs)"
                  >
                    <strong>{{ formatEvidenceTime(hit.startMs) }}</strong>
                    <small>{{ hit.source || '视频证据' }}</small>
                    <span>{{ hit.snippet || '该时间段暂无可展示文本' }}</span>
                  </button>
                </div>
              </div>
              <div class="markdown-content" v-html="renderedMarkdown" @click="seekEvidence"></div>
              <details v-if="sidebar.plan?.tasks?.length || traceStages.length" class="agent-inspector">
                <summary>Agent 是怎么得出这个结果的</summary>
                <div class="agent-inspector-content">
                  <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
                    <span class="meta-label">Planner 拆出的任务</span>
                    <div v-if="sidebar.editingPlan" class="plan-editor">
                      <div v-for="(_, index) in sidebar.planDraft" :key="index" class="plan-editor-row">
                        <input v-model="sidebar.planDraft[index]" maxlength="500" :aria-label="`任务 ${index + 1}`" />
                        <button type="button" title="删除任务" :aria-label="`删除任务 ${index + 1}`" @click="removePlanTask(index)">
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><line x1="6" y1="6" x2="18" y2="18"></line><line x1="18" y1="6" x2="6" y2="18"></line></svg>
                        </button>
                      </div>
                      <button v-if="sidebar.planDraft.length < 5" type="button" @click="addPlanTask">添加任务</button>
                      <div class="plan-editor-actions">
                        <button type="button" @click="cancelPlanEdit">取消</button>
                        <button type="button" class="is-primary" :disabled="sidebar.rerunLoading" @click="rerunWithPlan">
                          {{ sidebar.rerunLoading ? '提交中' : '按新计划重跑' }}
                        </button>
                      </div>
                    </div>
                    <template v-else>
                      <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
                      <button type="button" class="plan-edit-trigger" @click="startPlanEdit">调整计划</button>
                    </template>
                  </div>
                  <div v-if="traceStages.length" class="agent-meta-block">
                    <span class="meta-label">执行轨迹</span>
                    <ol class="stage-list">
                      <li v-for="stage in traceStages" :key="stage[0]"><span>{{ stage[0] }}</span><span class="stage-time">{{ stage[1] }}</span></li>
                    </ol>
                  </div>
                  <dl v-if="sidebar.evaluation && Object.keys(sidebar.evaluation).length" class="quality-row">
                    <div><dt>结构完整</dt><dd>{{ sidebar.evaluation.structuredValid ? '通过' : '待完善' }}</dd></div>
                    <div><dt>证据支持率</dt><dd>{{ formatPercent(sidebar.evaluation.evidenceSupportRate) }}</dd></div>
                    <div><dt>Critic 校验</dt><dd>{{ sidebar.evaluation.criticPassed ? '通过' : '达到轮次上限' }}</dd></div>
                  </dl>
                </div>
              </details>
              <div class="follow-up-box">
                <textarea
                    v-model="sidebar.followUp"
                    aria-label="基于视频继续追问"
                    maxlength="500"
                    placeholder="基于这个视频继续提问（Ctrl / ⌘ + Enter 发送）"
                    @keydown.ctrl.enter.prevent="submitFollowUp"
                    @keydown.meta.enter.prevent="submitFollowUp"
                ></textarea>
                <button :disabled="sidebar.followUpLoading || !sidebar.followUp.trim()" @click="submitFollowUp">
                  {{ sidebar.followUpLoading ? '分析中' : '追问' }}
                </button>
              </div>
              <div class="feedback-row">
                <span>这个结果有帮助吗？</span>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === 1 }" :aria-pressed="sidebar.feedback === 1" @click="sendFeedback(1)" title="有帮助">有帮助</button>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === -1 }" :aria-pressed="sidebar.feedback === -1" @click="sendFeedback(-1)" title="需改进">需改进</button>
              </div>
            </div>
            <div v-else class="text-content">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <template v-if="sidebar.content">
                <div class="result-actions">
                  <button type="button" @click="copyResult">复制全文</button>
                  <button type="button" @click="downloadResult">导出文本</button>
                </div>
                <p class="text-meta">{{ transcriptMeta }}</p>
                <pre>{{ sidebar.content }}</pre>
              </template>
              <p v-else-if="!sidebar.error" class="text-meta">这个视频还没有可展示的转写文本。</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="showAuthModal" class="auth-backdrop" @click.self="closeAuthModal">
        <div
            ref="authPanel"
            class="auth-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-title"
            @keydown="trapAuthFocus"
        >
          <div class="auth-header">
            <h2 id="auth-title" class="auth-title">{{ authMode === 'login' ? '登录 VidLore' : '注册 VidLore 账号' }}</h2>
            <button class="close-btn" @click="closeAuthModal" aria-label="关闭登录窗口">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="6" y1="6" x2="18" y2="18"></line><line x1="18" y1="6" x2="6" y2="18"></line></svg>
            </button>
          </div>
          <form class="auth-body" @submit.prevent="handleAuth">
            <div class="input-group">
              <label for="auth-username">用户名</label>
              <input id="auth-username" v-model="authForm.username" type="text" placeholder="字母、数字或下划线" autocomplete="username" autofocus />
            </div>
            <div class="input-group">
              <label for="auth-password">密码</label>
              <input id="auth-password" v-model="authForm.password" type="password" placeholder="至少 8 位" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" />
            </div>
            <div class="input-group" v-if="authMode === 'register'">
              <label for="auth-nickname">昵称</label>
              <input id="auth-nickname" v-model="authForm.nickname" type="text" placeholder="显示在右上角的名字" autocomplete="nickname" />
            </div>
            <button type="submit" class="primary-btn" :disabled="authLoading">
              <span v-if="!authLoading">{{ authMode === 'login' ? '登录' : '注册' }}</span>
              <span v-else>{{ authMode === 'login' ? '正在登录…' : '正在注册…' }}</span>
            </button>
            <div class="auth-toggle">
              <span class="toggle-text">{{ authMode === 'login' ? '还没有账号？' : '已经有账号？' }}</span>
              <button type="button" class="toggle-link" :disabled="authLoading" @click="switchAuthMode()">{{ authMode === 'login' ? '注册一个' : '去登录' }}</button>
            </div>
            <p
                v-if="authMessage"
                class="auth-msg"
                :class="{'error': authError}"
                :role="authError ? 'alert' : 'status'"
                aria-live="polite"
            >{{ authMessage }}</p>
          </form>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { apiRequest, captureAuthSession, clearAuthToken, hasAuthToken, setAuthToken } from './api'
import {
  forgetUploadProgress,
  formatBytes,
  formatDurationText,
  hasUploadProgress,
  uploadVideoInChunks,
  validateVideoFile
} from './chunkUpload'
import { DEMO_ITEM } from './demoData'
import { createTaskStreams } from './taskEvents'
import { useAnalysisWorkspace } from './useAnalysisWorkspace'

// --- 变量定义 ---
const DEMO_MODE = new URLSearchParams(window.location.search).has('demo')
const MESSAGE_TIMEOUT_MS = 4000
const file = ref(null)
const videoUrl = ref('')
const message = ref('')
const messageIsError = ref(false)
const uploading = ref(false)
const uploadProgress = ref({ label: '准备上传', filename: '', percent: null, detail: '', warning: '' })
const uploadAbort = ref(null)
const resumableFile = ref(null)
const resumableChunks = ref({ done: 0, total: 0 })
const list = ref([])
const listLoading = ref(false)
const listError = ref('')
let listRequestVersion = 0
let uploadRequestVersion = 0
let authRequestVersion = 0
const searchQuery = ref('')
const videoPlayer = ref(null)
const sidebarPanel = ref(null)
const sidebarBody = ref(null)
const authPanel = ref(null)
const deletingId = ref(null)
const isOffline = ref(typeof navigator !== 'undefined' && navigator.onLine === false)
const activeTasks = ref([])
const elapsedSeconds = ref(0)
const visibleList = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  if (!query) return list.value
  return list.value.filter(item => item.filename?.toLocaleLowerCase().includes(query))
})
const isDragOver = ref(false)
const currentUser = ref(null)
const showAuthModal = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const authMessage = ref('')
const authError = ref(false)
const authForm = ref({ username: '', password: '', nickname: '' })
const taskStreams = createTaskStreams({
  onActiveChange: tasks => { activeTasks.value = tasks }
})
let dragDepth = 0
let messageTimer = null
let elapsedTimer = null
let lastUploadProgress = {}
let focusBeforeAuth = null
let focusBeforeSidebar = null

// --- 状态展示 ---
const activeTaskOf = mediaId => activeTasks.value.find(task => String(task.id) === String(mediaId))

const systemStatusText = computed(() => {
  if (isOffline.value) return '网络已断开'
  if (uploading.value) {
    return uploadProgress.value.percent !== null
      ? `上传 ${uploadProgress.value.percent}%`
      : '处理中'
  }
  if (activeTasks.value.length) return `后台任务 ${activeTasks.value.length}`
  return '系统就绪'
})

const cardStatusClass = item => {
  if (activeTaskOf(item.id)) return 'processing'
  return mediaStatusClass(item.status)
}
const cardStatusLabel = item => {
  const task = activeTaskOf(item.id)
  if (task) return task.type === 'ai' ? '分析中' : '转写中'
  return mediaStatusLabel(item.status)
}
const cardStatusTitle = item => {
  const task = activeTaskOf(item.id)
  if (!task) return null
  return task.type === 'ai'
    ? 'AI 分析正在后台执行，完成后会提示你'
    : '文字提取正在后台执行，完成后会提示你'
}
// 仅用于展示：链接导入的视频文件名是 WEB_<uuid>.mp4，列表里换成可读的名字并去掉扩展名
const displayName = item => {
  const name = item.filename || ''
  if (/^WEB_[0-9a-f-]{36}\./i.test(name)) return '在线导入的视频'
  return name.replace(/\.[^.]+$/, '') || name
}

// 仅用于展示：后端没有生成封面，视频库直接截取原视频第 8 秒的画面；取不到时保留纯色封面
const COVER_FRAME_SECONDS = 8
const coverUrls = ref({})
const loadCovers = async items => {
  const isCurrentSession = captureAuthSession()
  const kept = {}
  for (const item of items) {
    if (item.id in coverUrls.value) kept[item.id] = coverUrls.value[item.id]
  }
  coverUrls.value = kept
  const pending = items.filter(item => item.status === 'COMPLETED' && !item.coverUrl && !(item.id in kept))
  await Promise.all(pending.map(async item => {
    try {
      const response = await apiRequest(`/media/playback?id=${item.id}`)
      const url = await response.text()
      if (!response.ok || !url || !isCurrentSession()) return
      coverUrls.value = { ...coverUrls.value, [item.id]: `${url}#t=${COVER_FRAME_SECONDS}` }
    } catch (error) {
      console.warn('Cover frame unavailable', error)
    }
  }))
}
watch(list, items => { if (!DEMO_MODE) loadCovers(items) })
const actionTitle = (item, label) => item.status === 'COMPLETED'
  ? null
  : `视频尚未处理完成，暂时无法${label}`

const elapsedLabel = computed(() => {
  const total = elapsedSeconds.value
  if (total < 1) return ''
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  return `${minutes}:${String(total % 60).padStart(2, '0')}`
})

const loadingHeadline = computed(() => {
  const fallback = sidebar.value.type === 'ai'
    ? 'Agent 正在分析视频证据'
    : '正在识别视频语音'
  const headline = sidebar.value.statusMessage || fallback
  // 用“已等待”而不是“已运行”：接管历史任务时计时是从打开面板算起的。
  return elapsedLabel.value ? `${headline} · 已等待 ${elapsedLabel.value}` : headline
})

const transcriptMeta = computed(() => {
  const length = sidebar.value.content?.length || 0
  if (!length) return ''
  return `共 ${length.toLocaleString('zh-CN')} 字`
})

const resumeHint = computed(() => {
  const target = resumableFile.value
  if (!target) return ''
  const { done, total } = resumableChunks.value
  const progress = total ? `已完成 ${Math.round((done / total) * 100)}%` : '已保留上传进度'
  return `${target.name} ${progress}，可继续未完成的上传`
})

// --- 核心业务逻辑 ---

const handleDragEnter = () => {
  dragDepth += 1
  isDragOver.value = true
}

// 拖过子元素也会触发 dragleave，用进出计数避免提示文案反复闪烁。
const handleDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (!dragDepth) isDragOver.value = false
}

const resetDragState = () => {
  dragDepth = 0
  isDragOver.value = false
}

/** 统一入口：登录、格式、体积三道校验全部在进入上传态之前完成。 */
const startUpload = async (selectedFile, extraFileCount = 0) => {
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }
  if (!selectedFile) return
  const invalid = validateVideoFile(selectedFile)
  if (invalid) {
    showMsg(`⚠️ ${invalid}`, true)
    return
  }
  if (extraFileCount > 0) {
    showMsg(`一次只处理一个视频，已选择 ${selectedFile.name}，其余 ${extraFileCount} 个已忽略`)
  }
  file.value = selectedFile
  videoUrl.value = ''
  await uploadFile()
}

const handleFileChange = async (e) => {
  const selected = e.target.files
  await startUpload(selected?.[0], Math.max(0, (selected?.length || 0) - 1))
  e.target.value = ''
}

const handleDrop = async (e) => {
  resetDragState()
  const dropped = e.dataTransfer?.files
  if (!dropped?.length) return
  await startUpload(dropped[0], dropped.length - 1)
}

const buildUploadWarning = progress => {
  if (progress.retryingCount) {
    return `网络不稳定，正在重试 ${progress.retryingCount} 个分片（第 ${progress.retryAttempt}/${progress.retryMaxAttempts} 次）`
  }
  if (progress.resumedChunks) {
    return `已续传：跳过 ${progress.resumedChunks} 个此前完成的分片`
  }
  return ''
}

const applyUploadProgress = progress => {
  lastUploadProgress = progress
  const merging = progress.phase === 'merging'
  const detail = [`${formatBytes(progress.uploadedBytes)} / ${formatBytes(progress.totalBytes)}`]
  detail.push(`分片 ${progress.completedChunks}/${progress.totalChunks}`)
  if (!merging && progress.bytesPerSecond) {
    detail.push(`${formatBytes(progress.bytesPerSecond)}/s`)
    const eta = formatDurationText(progress.etaSeconds)
    if (eta) detail.push(`剩余约 ${eta}`)
  }
  uploadProgress.value = {
    label: merging ? '分片已全部送达，正在服务端合并' : '正在安全上传',
    filename: file.value?.name || uploadProgress.value.filename,
    percent: progress.percent,
    detail: detail.join(' · '),
    warning: buildUploadWarning(progress)
  }
}

const rememberResumableUpload = target => {
  if (!target || !hasUploadProgress(target, currentUser.value?.id)) {
    resumableFile.value = null
    return
  }
  resumableFile.value = target
  resumableChunks.value = {
    done: lastUploadProgress.completedChunks || 0,
    total: lastUploadProgress.totalChunks || 0
  }
}

const uploadFile = async () => {
  const target = file.value
  if (!target) return
  if (DEMO_MODE) {
    showMsg('演示模式：已模拟完成分片上传')
    return
  }

  const controller = new AbortController()
  const requestVersion = ++uploadRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentUpload = () => requestVersion === uploadRequestVersion && isCurrentSession()
  uploadAbort.value = controller
  uploading.value = true
  resumableFile.value = null
  lastUploadProgress = {}
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: hasUploadProgress(target, uploadUserId) ? '正在核对已上传分片' : '准备分片上传',
    filename: target.name,
    percent: 0,
    detail: `0 B / ${formatBytes(target.size)}`,
    warning: ''
  }

  try {
    const uploadedMedia = await uploadVideoInChunks(target, progress => {
      if (isCurrentUpload()) applyUploadProgress(progress)
    }, controller.signal, uploadUserId)
    if (!isCurrentUpload()) return
    resumableFile.value = null
    showMsg(`✅ ${target.name} 上传完成`)
    await fetchList({ notify: true })
    if (!isCurrentUpload()) return
    openAgent(uploadedMedia)
  } catch (error) {
    if (!isCurrentUpload()) return
    rememberResumableUpload(target)
    if (error?.aborted) {
      showMsg('上传已取消，进度已保留，可点“继续上传”接着传')
      return
    }
    console.error(error)
    showMsg(
      resumableFile.value
        ? `❌ 上传中断：${error.message}（进度已保留，可继续上传）`
        : `❌ 上传失败：${error.message}`,
      true
    )
  } finally {
    if (isCurrentUpload()) {
      uploading.value = false
      uploadAbort.value = null
      file.value = null
    }
  }
}

const cancelUpload = () => {
  if (!uploadAbort.value) return
  uploadProgress.value = { ...uploadProgress.value, label: '正在取消上传', warning: '' }
  uploadAbort.value.abort()
}

const resumeUpload = async () => {
  const target = resumableFile.value
  if (!target || uploading.value) return
  file.value = target
  await uploadFile()
}

const discardResumableUpload = () => {
  forgetUploadProgress(resumableFile.value, currentUser.value?.id)
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  showMsg('已清除保留的上传进度，下次将从头开始')
}

const handleUrlUpload = async () => {
  const normalizedUrl = videoUrl.value.trim()
  if (!normalizedUrl) return
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (DEMO_MODE) {
    videoUrl.value = ''
    showMsg('演示模式：已模拟完成链接解析')
    return
  }

  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }

  let parsedUrl
  try {
    parsedUrl = new URL(normalizedUrl)
  } catch {
    parsedUrl = null
  }
  if (!parsedUrl || !['http:', 'https:'].includes(parsedUrl.protocol)) {
    showMsg('⚠️ 请输入合法的 http/https 链接', true)
    return
  }

  uploading.value = true
  const requestVersion = ++uploadRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentUpload = () => requestVersion === uploadRequestVersion && isCurrentSession()
  uploadProgress.value = {
    label: '正在解析视频链接',
    filename: parsedUrl.hostname,
    percent: null,
    detail: '服务端正在拉取源视频，时长取决于源站速度',
    warning: ''
  }
  messageIsError.value = false
  message.value = '正在解析链接并极速下载 (低码率模式)...'

  const formData = new FormData()
  formData.append('url', normalizedUrl)

  try {
    const res = await apiRequest('/media/upload-url', {
      method: 'POST',
      body: formData
    })
    if (!res.ok) throw new Error(await res.text())
    const uploadedMedia = await res.json()
    if (!isCurrentUpload()) return

    showMsg('✅ 链接资源已入库')
    videoUrl.value = ''
    await fetchList({ notify: true })
    if (!isCurrentUpload()) return
    openAgent(uploadedMedia)
  } catch (error) {
    console.error(error)
    if (!isCurrentUpload()) return
    let errMsg = error.message
    if (errMsg.includes("Unsupported URL")) errMsg = "不支持该平台链接"
    showMsg('❌ 解析失败: ' + errMsg, true)
  } finally {
    if (isCurrentUpload()) uploading.value = false
  }
}

/** 成功提示自动消失；错误提示保留到用户点掉，避免关键失败原因 4 秒后就没了。 */
const showMsg = (msg, isError = false) => {
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = msg
  messageIsError.value = isError
  if (isError) return
  messageTimer = setTimeout(() => {
    if (message.value !== msg) return
    message.value = ''
    messageIsError.value = false
  }, MESSAGE_TIMEOUT_MS)
}

const dismissMessage = () => {
  if (!messageIsError.value) return
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = ''
  messageIsError.value = false
}

const fetchList = async ({ notify = false } = {}) => {
  const requestVersion = ++listRequestVersion
  const isCurrentSession = captureAuthSession()
  const isCurrentRequest = () => requestVersion === listRequestVersion && isCurrentSession()
  if (DEMO_MODE) return list.value
  if (!currentUser.value) {
    list.value = []
    return list.value
  }
  listLoading.value = true
  listError.value = ''
  try {
    // 带时间戳绕开浏览器缓存，避免删除/新增之后列表还是旧的。
    const res = await apiRequest(`/media/list?_t=${Date.now()}`)
    if (res.status === 401) return null
    if (!res.ok) throw new Error('加载视频列表失败')
    const media = await res.json()
    if (!isCurrentRequest()) return null
    list.value = media
  } catch (error) {
    if (!isCurrentRequest()) return null
    console.error(error)
    listError.value = '视频资料库加载失败，请稍后重试'
    if (notify) showMsg('视频资料库加载失败，请稍后刷新', true)
    return null
  } finally {
    if (isCurrentRequest()) listLoading.value = false
  }
  return list.value
}

const mediaStatusClass = status => ['COMPLETED', 'PROCESSING', 'FAILED'].includes(status)
  ? status.toLowerCase()
  : 'unknown'
const mediaStatusLabel = status => ({
  COMPLETED: '可分析',
  PROCESSING: '处理中',
  FAILED: '处理失败'
})[status] || '等待处理'

const {
  sidebar,
  goalPresets,
  analysisModes,
  traceStages,
  renderedMarkdown,
  transcribe,
  closeSidebar,
  openAgent,
  submitAgent,
  startNewAnalysis,
  showDemoResult,
  startPlanEdit,
  cancelPlanEdit,
  addPlanTask,
  removePlanTask,
  rerunWithPlan,
  submitFollowUp,
  searchEvidence,
  sendFeedback,
  retryPlayback,
  handlePlaybackError,
  resetWorkspace,
  discardMediaWorkspace,
  formatPercent
} = useAnalysisWorkspace({
  demoMode: DEMO_MODE,
  taskStreams,
  showMessage: showMsg,
  refreshMediaList: fetchList,
  findMediaItem: id => list.value.find(item => item.id === id),
  onAnswerAppended: () => scrollToLatestAnswer()
})

// 仅用于展示：面板标题换成可读的视频名，原始标题里是文件名
const sidebarHeading = computed(() => {
  const item = list.value.find(entry => entry.id === sidebar.value.mediaId)
  if (!item) return sidebar.value.title
  return `${sidebar.value.type === 'ai' ? 'AI 分析' : '文字稿'}：${displayName(item)}`
})

/** 追问的答案追加在长文末尾，主动滚过去，否则用户会以为“点了没反应”。 */
const scrollToLatestAnswer = async () => {
  await nextTick()
  const container = sidebarBody.value?.querySelector('.markdown-content')
  if (!container) return
  const headings = container.querySelectorAll('h2, h3')
  const anchor = headings.length ? headings[headings.length - 1] : container.lastElementChild
  anchor?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const seekVideo = seconds => {
  if (!Number.isFinite(seconds)) return
  const player = videoPlayer.value
  if (!player) {
    if (sidebar.value.playbackError) {
      showMsg('原视频加载失败，无法跳转，可先点“重新加载”', true)
    } else if (sidebar.value.playbackLoading) {
      showMsg('原视频还在载入，稍等一下再点这个时间戳')
    } else {
      showMsg('这个视频暂时没有可播放的原片，无法跳转', true)
    }
    return
  }
  if (player.readyState === 0) {
    player.addEventListener('loadedmetadata', () => seekVideo(seconds), { once: true })
    return
  }
  const duration = player.duration
  const maxTime = Number.isFinite(duration) ? Math.max(0, duration - 0.1) : Number.MAX_SAFE_INTEGER
  player.currentTime = Math.min(Math.max(0, seconds), maxTime)
  player.play().catch(() => {})
  player.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

const seekEvidence = event => {
  const link = event.target.closest('a[href^="#video-t="]')
  if (!link) return
  event.preventDefault()
  seekVideo(Number(link.getAttribute('href').split('=')[1]))
}

const seekToEvidence = timestampMs => seekVideo(Number(timestampMs) / 1000)
const formatEvidenceTime = timestampMs => {
  const seconds = Math.max(0, Math.floor(Number(timestampMs) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const time = `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${time}` : time
}

/** Clipboard API 在非 HTTPS 环境不可用，这里保留一条降级路径，避免“复制失败”变成死路。 */
const copyToClipboard = async text => {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 继续走下面的降级方案。
  }
  try {
    const scratch = document.createElement('textarea')
    scratch.value = text
    scratch.setAttribute('readonly', '')
    scratch.style.position = 'fixed'
    scratch.style.top = '0'
    scratch.style.opacity = '0'
    document.body.appendChild(scratch)
    scratch.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(scratch)
    return copied
  } catch {
    return false
  }
}

const copyResult = async () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可复制的内容', true)
    return
  }
  const label = sidebar.value.type === 'ai' ? '分析结果' : '转写全文'
  if (await copyToClipboard(content)) showMsg(`${label}已复制`)
  else showMsg('复制失败，请手动选中内容后复制', true)
}

const resultFileBaseName = () => {
  const title = sidebar.value.title || ''
  const raw = title.split(' · ').slice(1).join(' · ') || title
  const cleaned = raw.replace(/\.[^/.]+$/, '').replace(/[\\/:*?"<>|]/g, '_').trim()
  return cleaned || (sidebar.value.type === 'ai' ? 'analysis' : 'transcript')
}

const downloadResult = () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可导出的内容', true)
    return
  }
  const isMarkdown = sidebar.value.type === 'ai'
  const blob = new Blob([content], {
    type: isMarkdown ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${resultFileBaseName()}.${isMarkdown ? 'md' : 'txt'}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 立刻 revoke 在部分浏览器会导致下载拿不到内容，延后一拍更稳。
  setTimeout(() => URL.revokeObjectURL(url), 0)
  showMsg(`已导出 ${link.download}`)
}

const deleteItem = async (item) => {
  if (DEMO_MODE) {
    list.value = list.value.filter(i => i.id !== item.id)
    discardMediaWorkspace(item.id)
    showMsg('演示任务已移除')
    return
  }
  if (deletingId.value) return
  const runningTask = activeTaskOf(item.id)
  const warning = runningTask
    ? '\n\n注意：该视频还有任务正在后台执行，删除后这次的结果会丢失。'
    : ''
  if (!confirm(`确认要永久删除 "${item.filename}" 吗？${warning}`)) return
  deletingId.value = item.id
  const isCurrentSession = captureAuthSession()
  try {
    const res = await apiRequest(`/media/delete?id=${item.id}`, { method: 'DELETE' })
    const text = await res.text()
    if (!isCurrentSession()) return
    if (res.ok) {
      listRequestVersion += 1
      listLoading.value = false
      showMsg(`已删除 ${item.filename}`)
      list.value = list.value.filter(i => i.id !== item.id)
      discardMediaWorkspace(item.id)
    } else {
      showMsg('❌ ' + text, true)
    }
  } catch (e) {
    if (!isCurrentSession()) return
    showMsg('❌ 删除请求失败', true)
  } finally {
    if (isCurrentSession()) deletingId.value = null
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return '--'
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const downloadAudio = async (item) => {
  if (DEMO_MODE) {
    showMsg(`演示模式：${item.filename} 音频已准备`)
    return
  }
  let fileName = item.filename || 'audio.mp3';
  fileName = fileName.replace(/\.[^/.]+$/, "") + ".mp3";
  try {
    showMsg('正在转码并下载...')
    const res = await apiRequest(`/analysis/download?id=${item.id}`)
    // 失败时后端返回的是 JSON 信封，api.js 会把 message 解包给 text()，
    // 这里读出来向上抛，避免把“视频不存在 / 无权访问 / 转码失败”统一显示成同一句话。
    if (!res.ok) throw new Error((await res.text()) || '请稍后重试')
    const blob = await res.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    showMsg('✅ 下载完成')
  } catch (e) {
    showMsg('音频下载失败：' + (e?.message || '请稍后重试'), true)
  }
}

const restoreFocus = element => {
  if (element?.isConnected && typeof element.focus === 'function') element.focus()
}

const openAuthModal = () => {
  if (showAuthModal.value) return
  focusBeforeAuth = document.activeElement
  showAuthModal.value = true
  authMessage.value = ''
  authForm.value = { username: '', password: '', nickname: '' }
}
const closeAuthModal = () => {
  authRequestVersion += 1
  authLoading.value = false
  showAuthModal.value = false
  restoreFocus(focusBeforeAuth)
  focusBeforeAuth = null
}
const closeActiveOverlay = () => {
  if (showAuthModal.value) closeAuthModal()
  else if (sidebar.value.visible) closeSidebar()
}
const handleKeydown = event => {
  if (event.key === 'Escape') closeActiveOverlay()
}

/** 弹窗内循环 Tab，键盘用户不会一路跳到被遮住的背景里。 */
const trapAuthFocus = event => {
  trapPanelFocus(event, authPanel.value)
}
const trapPanelFocus = (event, panel) => {
  if (event.key !== 'Tab' || !panel) return
  const focusable = [...panel.querySelectorAll('button, input, textarea, select, a[href], summary, [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.disabled && element.offsetParent !== null)
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || active === panel || !panel.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}

const switchAuthMode = ({ keepMessage = false } = {}) => {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  if (!keepMessage) authMessage.value = ''
}
const handleAuth = async () => {
  if (authLoading.value) return
  if (!authForm.value.username || !authForm.value.password) {
    authMessage.value = '请输入完整的账号和密码'
    authError.value = true
    return
  }
  authLoading.value = true
  authMessage.value = ''
  const requestVersion = ++authRequestVersion
  const mode = authMode.value
  const endpoint = mode === 'login' ? '/user/login' : '/user/register'
  try {
    const res = await apiRequest(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(authForm.value)
    })
    if (requestVersion !== authRequestVersion) return
    if (!res.ok) {
      const message = await res.text()
      if (requestVersion !== authRequestVersion) return
      authMessage.value = message || `请求失败（HTTP ${res.status}）`
      authError.value = true
      return
    }
    const data = await res.json().catch(() => null)
    if (requestVersion !== authRequestVersion) return
    if (!data?.userInfo) {
      authMessage.value = '服务端返回异常，请稍后重试'
      authError.value = true
      return
    }
    if (mode === 'login') {
      setAuthToken(data.token)
      currentUser.value = data.userInfo
      localStorage.setItem('user', JSON.stringify(data.userInfo))
      closeAuthModal()
      showMsg(`欢迎回来，${data.userInfo.nickname}`)
      fetchList({ notify: true })
    } else {
      authMessage.value = '注册成功，账号密码已保留，直接点“立即登录”即可'
      authError.value = false
      switchAuthMode({ keepMessage: true })
    }
  } catch (e) {
    if (requestVersion !== authRequestVersion) return
    console.error(e)
    authMessage.value = e?.message || '网络连接错误'
    authError.value = true
  } finally {
    if (requestVersion === authRequestVersion) authLoading.value = false
  }
}
/** 退出与登录失效走同一套清理，避免两处漏掉不同的字段。 */
const resetSessionState = () => {
  uploadRequestVersion += 1
  listRequestVersion += 1
  authRequestVersion += 1
  authLoading.value = false
  deletingId.value = null
  listLoading.value = false
  listError.value = ''
  uploadAbort.value?.abort()
  uploadAbort.value = null
  taskStreams.stopAll()
  resetWorkspace()
  currentUser.value = null
  list.value = []
  searchQuery.value = ''
  videoUrl.value = ''
  file.value = null
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  uploading.value = false
  localStorage.removeItem('user')
}

const logout = () => {
  if (hasAuthToken()) {
    apiRequest('/user/logout', { method: 'POST' }).catch(() => {})
  }
  resetSessionState()
  clearAuthToken()
  showMsg('已退出系统')
}

const handleAuthExpired = () => {
  resetSessionState()
  showMsg('登录状态已失效，请重新登录', true)
  openAuthModal()
}

const handleOnline = () => {
  isOffline.value = false
  showMsg('网络已恢复，正在同步最新状态')
  if (currentUser.value) fetchList()
}

const handleOffline = () => {
  isOffline.value = true
  showMsg('网络已断开：上传会自动重试，后台任务会在恢复后继续', true)
}

// 上传中误关标签页会白丢已传分片，这里让浏览器先问一句。
const handleBeforeUnload = event => {
  if (!uploading.value) return
  event.preventDefault()
  event.returnValue = ''
}

// 打开弹层时挂上标记类，锁背景滚动的规则只在窄屏生效（见样式里的说明）。
const overlayOpen = computed(() => sidebar.value.visible || showAuthModal.value)
watch(overlayOpen, open => {
  document.body.classList.toggle('overlay-open', open)
})

watch(() => sidebar.value.visible, async visible => {
  if (visible) {
    focusBeforeSidebar = document.activeElement
    await nextTick()
    sidebarPanel.value?.focus()
    return
  }
  restoreFocus(focusBeforeSidebar)
  focusBeforeSidebar = null
})

// 长任务给一个时间锚点，用户才不会怀疑是不是卡死了。
watch(() => sidebar.value.loading, loading => {
  clearInterval(elapsedTimer)
  elapsedTimer = null
  elapsedSeconds.value = 0
  if (!loading) return
  const startedAt = Date.now()
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 1000)
})

onMounted(() => {
  window.addEventListener('auth-expired', handleAuthExpired)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('beforeunload', handleBeforeUnload)
  if (DEMO_MODE) {
    currentUser.value = { id: 1, nickname: 'Agent Demo' }
    list.value = [DEMO_ITEM]
    openAgent(DEMO_ITEM)
    showDemoResult()
    return
  }
  const savedUser = localStorage.getItem('user')
  if (savedUser && hasAuthToken()) {
    try {
      currentUser.value = JSON.parse(savedUser)
    } catch(e) {}
  }
  fetchList({ notify: Boolean(currentUser.value) })
})
onUnmounted(() => {
  uploadRequestVersion += 1
  listRequestVersion += 1
  authRequestVersion += 1
  resetWorkspace()
  window.removeEventListener('auth-expired', handleAuthExpired)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearTimeout(messageTimer)
  clearInterval(elapsedTimer)
  uploadAbort.value?.abort()
  document.body.classList.remove('overlay-open')
  taskStreams.stopAll()
})
</script>

<style>
/* 字体在 index.html 里用非阻塞 <link> 加载，这里不 @import，避免阻塞首屏 */

:root {
  --paper: #eef1f4;
  --sheet: #ffffff;
  --ink: #18202e;
  --ink-soft: #5a6475;
  --ink-faint: #8a93a3;
  --rule: #cdd4dd;
  --rule-soft: #e2e7ed;
  --cobalt: #2b4acb;
  --cobalt-deep: #1f379e;
  --cobalt-wash: #e7ecfb;
  --marker: #ffe066;
  --marker-strong: #ffd43b;
  --ok: #1d7a4a;
  --danger: #b42318;
  --danger-wash: #fdecea;
  --serif: 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  --sans: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --control-radius: 6px;
  --surface-radius: 14px;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

html, body, #app { min-height: 100vh; background: var(--paper); }
body { overflow-x: hidden; }

.app-stage {
  min-height: 100vh;
  color: var(--ink);
  font-family: var(--sans);
  font-size: 16px;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
}

button { font: inherit; color: inherit; }

/* 顶栏 */
.masthead { position: sticky; top: 0; z-index: 100; background: rgba(238, 241, 244, 0.9); backdrop-filter: blur(10px); border-bottom: 1px solid var(--rule); }
.masthead-inner { max-width: 1240px; margin: 0 auto; padding: 12px 32px; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.wordmark { font-family: var(--serif); font-size: 1.5rem; font-weight: 900; letter-spacing: 0.01em; color: var(--ink); line-height: 1; }
.masthead-tools { display: flex; align-items: center; gap: 10px; }

.status-line { display: flex; align-items: center; gap: 8px; margin-right: 8px; padding: 5px 12px; border-radius: 999px; background: var(--sheet); border: 1px solid var(--rule); color: var(--ink-soft); font-size: 0.8125rem; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); }
.status-line.is-active .status-dot { background: var(--cobalt); animation: pulse 1.4s ease-in-out infinite; }
.status-line.is-offline { color: var(--danger); border-color: var(--danger); }
.status-line.is-offline .status-dot { background: var(--danger); }

.ghost-btn, .auth-btn { border-radius: var(--control-radius); padding: 7px 16px; font-size: 0.9375rem; font-weight: 500; cursor: pointer; }
.ghost-btn { border: 0; background: transparent; color: var(--ink); }
.ghost-btn:hover { background: var(--rule-soft); }
.auth-btn { border: 0; background: var(--ink); color: var(--sheet); }
.auth-btn:hover { background: var(--cobalt); }
.user-profile { display: flex; align-items: center; gap: 8px; font-size: 0.9375rem; }
.user-avatar { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--cobalt); color: var(--sheet); font-size: 0.875rem; font-weight: 700; }
.user-name { font-weight: 500; }
.logout-btn { display: grid; place-items: center; width: 32px; height: 32px; border: 0; border-radius: var(--control-radius); background: transparent; color: var(--ink-soft); cursor: pointer; }
.logout-btn:hover { color: var(--danger); background: var(--danger-wash); }

/* 首屏：未登录是落地页，登录后只保留一条导入栏 */
.main-container { max-width: 1240px; margin: 0 auto; padding: 88px 32px 96px; }
.main-container.is-app { padding-top: 32px; }
.hero { max-width: 780px; margin: 0 auto 72px; text-align: center; }
.hero.is-compact { max-width: none; margin-bottom: 40px; text-align: left; }
.hero-title { font-family: var(--serif); font-weight: 900; font-size: clamp(2.25rem, 5.2vw, 4rem); line-height: 1.18; letter-spacing: 0.01em; }
.hero-title span { display: inline-block; }
.hero-lede { margin: 18px auto 0; max-width: 30em; color: var(--ink-soft); font-size: 1.1875rem; line-height: 1.7; text-wrap: balance; }

/* 导入框：落地页里是主角，登录后收成一行 */
.composer { position: relative; margin-top: 40px; padding: 10px; background: var(--sheet); border: 1px solid var(--rule); border-radius: 18px; box-shadow: 0 18px 40px -24px rgba(24, 32, 46, 0.35); text-align: left; transition: border-color 0.2s, box-shadow 0.2s; }
.composer.is-dragover { border-color: var(--cobalt); box-shadow: 0 0 0 5px var(--cobalt-wash); }

.composer-link { display: flex; align-items: center; gap: 10px; padding: 6px 6px 6px 16px; border-radius: 12px; background: var(--paper); }
.composer-link:focus-within { background: var(--sheet); box-shadow: inset 0 0 0 2px var(--cobalt); }
.composer-link-icon { flex: none; color: var(--ink-faint); }
.composer-link input { flex: 1; min-width: 0; height: 48px; border: 0; outline: 0; background: transparent; color: var(--ink); font-size: 1.0625rem; }
.composer-link input::placeholder { color: var(--ink-faint); }
.composer-go { flex: none; height: 44px; padding: 0 24px; border: 0; border-radius: 10px; background: var(--cobalt); color: var(--sheet); font-size: 1rem; font-weight: 500; cursor: pointer; }
.composer-go:hover:not(:disabled) { background: var(--cobalt-deep); }
.composer-go:disabled { background: var(--rule); color: var(--sheet); cursor: not-allowed; }

.composer-or { display: flex; align-items: center; gap: 12px; margin: 8px 16px; color: var(--ink-faint); font-size: 0.8125rem; }
.composer-or::before, .composer-or::after { content: ''; flex: 1; height: 1px; background: var(--rule-soft); }

.composer-file { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 6px 10px; padding: 16px; border: 1.5px dashed var(--rule); border-radius: 12px; color: var(--ink); cursor: pointer; transition: background 0.2s, border-color 0.2s; }
.composer-file:hover, .composer.is-dragover .composer-file { border-color: var(--cobalt); background: var(--cobalt-wash); color: var(--cobalt-deep); }
.composer-file-title { font-weight: 500; }
.composer-file-hint { color: var(--ink-soft); font-size: 0.875rem; }

.composer.is-compact { margin-top: 0; display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: center; gap: 4px; padding: 8px; border-radius: 14px; box-shadow: none; }
.composer.is-compact.processing { display: block; }
.composer.is-compact .composer-link input { height: 40px; font-size: 1rem; }
.composer.is-compact .composer-go { height: 38px; padding: 0 18px; font-size: 0.9375rem; }
.composer.is-compact .composer-or { margin: 0 6px; }
.composer.is-compact .composer-or::before, .composer.is-compact .composer-or::after { display: none; }
.composer.is-compact .composer-file { padding: 0 16px; height: 52px; border-style: solid; border-color: var(--rule); }
.composer.is-compact .composer-file-hint { display: none; }

/* 上传中：在导入框里原地展示进度 */
.composer-busy { padding: 18px 18px 16px; display: flex; flex-direction: column; gap: 10px; }
.composer-busy-head { display: flex; align-items: center; gap: 14px; }
.composer-busy-text { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.busy-text { font-weight: 700; }
.busy-file { color: var(--ink-soft); font-size: 0.875rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.busy-cancel { flex: none; border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink-soft); padding: 6px 14px; font-size: 0.875rem; cursor: pointer; }
.busy-cancel:hover { border-color: var(--danger); color: var(--danger); }
.upload-progress { height: 6px; border-radius: 3px; background: var(--rule-soft); overflow: hidden; }
.upload-progress span { display: block; height: 100%; background: var(--cobalt); transition: width 0.25s ease; }
.busy-stat { color: var(--ink-soft); font-size: 0.8125rem; }
.busy-warning { color: var(--danger); font-size: 0.8125rem; }

.quantum-loader { flex: none; width: 28px; height: 28px; border: 3px solid var(--rule-soft); border-top-color: var(--cobalt); border-radius: 50%; animation: spin 0.9s linear infinite; }
.quantum-loader.small { margin: 0 auto; }

.source-list { list-style: none; display: flex; justify-content: center; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.source-list li { padding: 4px 12px; border-radius: 999px; background: var(--rule-soft); color: var(--ink-soft); font-size: 0.8125rem; }

.upload-resume { margin-top: 14px; padding: 12px 16px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; background: var(--sheet); border: 1px solid var(--rule); border-left: 3px solid var(--cobalt); border-radius: var(--control-radius); color: var(--ink-soft); font-size: 0.875rem; text-align: left; }
.upload-resume-actions { display: flex; gap: 8px; }
.upload-resume button { border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 6px 12px; cursor: pointer; }
.upload-resume button.is-primary { border-color: var(--cobalt); background: var(--cobalt); color: var(--sheet); }
.upload-resume button:hover { border-color: var(--cobalt); }

/* 全局提示：固定在底部，不随长列表滚走 */
.notification-bar { position: fixed; left: 50%; bottom: 28px; z-index: 2100; transform: translateX(-50%); max-width: min(560px, calc(100vw - 32px)); padding: 12px 20px; border-radius: 10px; background: var(--ink); color: var(--sheet); font-size: 0.9375rem; text-align: left; box-shadow: 0 12px 32px -12px rgba(24, 32, 46, 0.45); }
.notification-bar.error { background: var(--danger); cursor: pointer; }
.toast-pop-enter-active, .toast-pop-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.toast-pop-enter-from, .toast-pop-leave-to { opacity: 0; transform: translate(-50%, 8px); }

/* 记号笔时间戳：整个界面唯一的黄色，只给“能跳回原视频的证据”用 */
.cite-mark,
.markdown-content a[href^="#video-t="],
.evidence-search-results strong {
  display: inline-block;
  padding: 0 0.3em;
  color: var(--ink);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  text-decoration: none;
  background: linear-gradient(176deg, transparent 8%, var(--marker) 8%, var(--marker) 90%, transparent 90%);
  border-radius: 2px;
}
.markdown-content a[href^="#video-t="] { cursor: pointer; }
.markdown-content a[href^="#video-t="]:hover { background: linear-gradient(176deg, transparent 8%, var(--marker-strong) 8%, var(--marker-strong) 90%, transparent 90%); text-decoration: underline; text-underline-offset: 3px; }

/* 落地页的产品预览：笔记里的黄色时间戳 = 进度条上的黄色刻度 */
.showcase { max-width: 1080px; margin: 0 auto; }
.showcase-frame { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); overflow: hidden; background: var(--sheet); border: 1px solid var(--rule); border-radius: 18px; box-shadow: 0 30px 60px -36px rgba(24, 32, 46, 0.45); }
.showcase-video { display: flex; flex-direction: column; background: #0f131a; }
.showcase-slide { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; min-height: 300px; padding: 28px; }
.slide-title { color: #f2f5fb; font-family: var(--serif); font-size: 1.5rem; font-weight: 700; }
.slide-tree { width: min(260px, 70%); height: auto; }
.slide-caption { color: #aeb9cf; font-size: 0.875rem; }
.showcase-scrubber { position: relative; height: 44px; margin: 0 20px; }
.showcase-scrubber::before { content: ''; position: absolute; left: 0; right: 0; top: 12px; height: 4px; border-radius: 2px; background: #2c3442; }
.scrubber-fill { position: absolute; left: 0; top: 12px; height: 4px; border-radius: 2px; background: #c9d3e6; }
.showcase-scrubber i { position: absolute; top: 7px; width: 6px; height: 14px; margin-left: -3px; border-radius: 2px; background: var(--marker); }
.showcase-scrubber i.is-current { top: 4px; width: 8px; height: 20px; margin-left: -4px; box-shadow: 0 0 0 3px rgba(255, 224, 102, 0.25); }
.scrubber-time { position: absolute; right: 0; bottom: 4px; color: #aeb9cf; font-size: 0.75rem; }

.showcase-notes { padding: 28px 32px; }
.showcase-goal { display: inline-block; margin-bottom: 18px; padding: 6px 12px; border-radius: 10px 10px 10px 2px; background: var(--cobalt-wash); color: var(--cobalt-deep); font-size: 0.875rem; }
.showcase-notes h3 { margin: 18px 0 8px; font-family: var(--serif); font-size: 1.125rem; font-weight: 900; }
.showcase-notes h3:first-of-type { margin-top: 0; }
.showcase-notes ul { padding-left: 1.2em; }
.showcase-notes li { margin-bottom: 8px; font-size: 0.9375rem; line-height: 1.7; }
.showcase-notes li::marker { color: var(--ink-faint); }
.showcase-notes li.is-current { margin-left: -0.6em; padding: 2px 0.6em; border-radius: 6px; background: var(--paper); }
.showcase-caption { margin-top: 16px; text-align: center; color: var(--ink-soft); font-size: 0.875rem; }

/* 视频库：封面网格 */
.section-header { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.library-title { display: flex; align-items: center; gap: 10px; }
.library-title h2 { font-family: var(--serif); font-size: 1.5rem; font-weight: 900; line-height: 1.2; }
.count-chip { min-width: 26px; padding: 1px 9px; border-radius: 999px; background: var(--rule-soft); color: var(--ink-soft); font-size: 0.8125rem; text-align: center; }
.library-search { width: min(280px, 42vw); display: flex; align-items: center; gap: 8px; padding: 8px 12px; border: 1px solid var(--rule); border-radius: 999px; background: var(--sheet); color: var(--ink-faint); }
.library-search:focus-within { border-color: var(--cobalt); color: var(--cobalt); }
.library-search input { width: 100%; border: 0; outline: 0; background: transparent; color: var(--ink); font: inherit; font-size: 0.9375rem; }

.media-grid { list-style: none; display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 24px; }
.media-card { display: flex; flex-direction: column; min-width: 0; }
.media-cover { position: relative; display: flex; align-items: flex-end; aspect-ratio: 16 / 10; padding: 18px 20px; border: 0; border-radius: 12px; overflow: hidden; color: #f5f7fb; text-align: left; cursor: pointer; }
.media-cover::after { content: ''; position: absolute; inset: 0; z-index: 1; background: rgba(15, 19, 26, 0); transition: background 0.2s; }
.cover-frame { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; background: #0f131a; pointer-events: none; }
/* 有真实画面时，底部压一层渐变，保证标题看得清 */
.media-cover.has-frame::before { content: ''; position: absolute; inset: 0; z-index: 1; background: linear-gradient(to top, rgba(10, 13, 20, 0.88) 0%, rgba(10, 13, 20, 0.35) 45%, rgba(10, 13, 20, 0) 75%); }
.media-cover.has-frame .cover-title { font-size: 1.125rem; }
.media-cover.tone-0 { background: #1b2740; }
.media-cover.tone-1 { background: #2b4acb; }
.media-cover.tone-2 { background: #155a5a; }
.media-cover.tone-3 { background: #3b3f4c; }
.cover-title { position: relative; z-index: 2; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; font-family: var(--serif); font-size: 1.3125rem; font-weight: 700; line-height: 1.35; overflow-wrap: anywhere; }
.cover-cta { position: absolute; z-index: 3; top: 14px; right: 14px; padding: 4px 10px; border-radius: 999px; background: var(--sheet); color: var(--ink); font-size: 0.8125rem; font-weight: 500; opacity: 0; transform: translateY(-4px); transition: opacity 0.2s, transform 0.2s; }
.media-cover:hover:not(:disabled)::after { background: rgba(15, 19, 26, 0.18); }
@media (hover: hover) { .media-cover:hover:not(:disabled) .cover-cta { opacity: 1; transform: translateY(0); } }
.media-cover:focus-visible .cover-cta { opacity: 1; transform: translateY(0); }
.media-cover:disabled { cursor: not-allowed; filter: saturate(0.4); opacity: 0.75; }

.media-body { padding: 12px 2px 0; }
.meta-tags { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; font-size: 0.8125rem; }
.time-tag { color: var(--ink-soft); }
.status-indicator { display: inline-flex; align-items: center; gap: 6px; font-weight: 500; }
.status-indicator::before { content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-indicator.completed { color: var(--ok); }
.status-indicator.processing { color: var(--cobalt); }
.status-indicator.processing::before { animation: pulse 1.4s ease-in-out infinite; }
.status-indicator.failed { color: var(--danger); }
.status-indicator.unknown { color: var(--ink-faint); }

.action-dock { display: flex; align-items: center; gap: 6px; margin-top: 10px; }
.dock-item { border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 5px 11px; font-size: 0.8125rem; white-space: nowrap; cursor: pointer; }
.dock-item:hover:not(:disabled) { border-color: var(--ink); }
.dock-item.ai-core { border-color: var(--cobalt); background: var(--cobalt); color: var(--sheet); font-weight: 500; }
.dock-item.ai-core:hover:not(:disabled) { background: var(--cobalt-deep); border-color: var(--cobalt-deep); }
.dock-item:disabled { opacity: 0.4; cursor: not-allowed; }
.delete-btn { display: grid; place-items: center; width: 30px; height: 30px; margin-left: auto; border: 0; border-radius: var(--control-radius); background: transparent; color: var(--ink-faint); cursor: pointer; }
.delete-btn:hover:not(:disabled) { color: var(--danger); background: var(--danger-wash); }
.delete-btn:disabled { cursor: progress; opacity: 0.5; }

.library-empty { padding: 48px 20px; text-align: center; color: var(--ink-soft); border: 1.5px dashed var(--rule); border-radius: var(--surface-radius); }
.library-empty button { margin-top: 12px; border: 0; background: transparent; color: var(--cobalt); font-weight: 500; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }

/* 分析面板：右侧滑出的一张白纸 */
.sidebar-backdrop { position: fixed; inset: 0; z-index: 998; background: rgba(24, 32, 46, 0.4); }
.sidebar-panel { position: fixed; top: 0; right: 0; z-index: 999; width: 860px; max-width: calc(100vw - 24px); height: 100%; display: flex; flex-direction: column; background: var(--sheet); transform: translateX(105%); visibility: hidden; transition: transform 0.38s cubic-bezier(0.2, 0.9, 0.25, 1), visibility 0s linear 0.38s; }
.sidebar-panel:focus { outline: none; }
.sidebar-panel.is-open { transform: translateX(0); visibility: visible; box-shadow: -24px 0 48px -24px rgba(24, 32, 46, 0.35); transition: transform 0.38s cubic-bezier(0.2, 0.9, 0.25, 1); }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 28px; border-bottom: 1px solid var(--rule); }
.sidebar-title { min-width: 0; font-family: var(--serif); font-size: 1.1875rem; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.close-btn { flex: none; display: grid; place-items: center; width: 36px; height: 36px; border: 0; border-radius: var(--control-radius); background: transparent; color: var(--ink-soft); cursor: pointer; }
.close-btn:hover { background: var(--paper); color: var(--ink); }
.sidebar-body { flex: 1; overflow-y: auto; padding: 28px 40px 48px; }

.video-evidence { margin: -28px -40px 28px; background: #0f131a; }
.video-evidence video { display: block; width: 100%; max-height: 420px; aspect-ratio: 16 / 9; background: #000; object-fit: contain; }
.video-evidence p, .video-evidence-loading { padding: 10px 40px; color: #b8c0cc; font-size: 0.8125rem; }
.video-evidence-loading { min-height: 120px; display: grid; place-items: center; }
.video-evidence-error { min-height: 120px; padding: 18px 40px; display: flex; align-items: center; justify-content: center; gap: 12px; color: #ffb4ab; }
.video-evidence-error button { border: 1px solid #ffb4ab; border-radius: var(--control-radius); background: transparent; color: #ffb4ab; padding: 6px 12px; cursor: pointer; }

.agent-composer { display: flex; flex-direction: column; gap: 28px; }
.composer-field { display: flex; flex-direction: column; gap: 12px; }
.agent-caption { font-family: var(--serif); font-size: 1.125rem; font-weight: 700; }
.inline-error { padding: 12px 14px; border-left: 3px solid var(--danger); border-radius: 0 var(--control-radius) var(--control-radius) 0; background: var(--danger-wash); color: var(--danger); line-height: 1.6; }
.agent-composer textarea, .follow-up-box textarea { width: 100%; min-height: 128px; resize: vertical; border: 1px solid var(--rule); border-radius: 8px; background: var(--sheet); color: var(--ink); padding: 14px 16px; font-size: 0.9375rem; line-height: 1.7; outline: none; }
.agent-composer textarea:focus, .follow-up-box textarea:focus { border-color: var(--cobalt); box-shadow: 0 0 0 3px var(--cobalt-wash); }
.field-counter { color: var(--ink-soft); font-size: 0.8125rem; text-align: right; }

.goal-presets { display: grid; gap: 8px; }
.agent-mode-row { grid-template-columns: repeat(5, minmax(0, 1fr)); }
.preset-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.goal-presets button { min-height: 76px; padding: 12px 14px; border: 1px solid var(--rule); border-radius: 8px; background: var(--sheet); text-align: left; cursor: pointer; }
.goal-presets strong, .goal-presets span { display: block; }
.goal-presets strong { margin-bottom: 4px; font-size: 0.9375rem; }
.goal-presets span { color: var(--ink-soft); font-size: 0.8125rem; line-height: 1.5; }
.goal-presets button:hover { border-color: var(--ink-soft); }
.goal-presets button.active { border-color: var(--cobalt); background: var(--cobalt-wash); box-shadow: inset 0 0 0 1px var(--cobalt); }
.goal-presets button.active strong { color: var(--cobalt-deep); }

.agent-run-btn { align-self: flex-start; min-width: 180px; border: 0; border-radius: var(--control-radius); background: var(--cobalt); color: var(--sheet); padding: 12px 28px; font-size: 1rem; font-weight: 500; cursor: pointer; }
.agent-run-btn:hover:not(:disabled) { background: var(--cobalt-deep); }
.agent-run-btn:disabled, .follow-up-box button:disabled { opacity: 0.4; cursor: not-allowed; }

.agent-running { display: flex; flex-direction: column; gap: 20px; }
.loading-state { min-height: 200px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; text-align: center; }
.loading-state p { max-width: 34rem; }
.stream-offline { color: var(--danger); font-size: 0.875rem; }
.loading-hint { color: var(--ink-soft); font-size: 0.875rem; }

.result-actions { display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 18px; }
.result-actions button { border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 7px 12px; font-size: 0.875rem; cursor: pointer; }
.result-actions button:hover:not(:disabled) { border-color: var(--ink); }
.result-actions button:disabled { opacity: 0.4; cursor: not-allowed; }

.evidence-search { margin: 0 0 32px; padding: 16px; border-radius: 10px; background: var(--paper); }
.evidence-search-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.evidence-search-form input { min-width: 0; border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 9px 12px; font-size: 0.9375rem; outline: none; }
.evidence-search-form input:focus { border-color: var(--cobalt); }
.evidence-search-form button { border: 1px solid var(--ink); border-radius: var(--control-radius); background: var(--ink); color: var(--sheet); padding: 0 16px; font-size: 0.875rem; cursor: pointer; }
.evidence-search-form button:disabled { opacity: 0.4; cursor: not-allowed; }
.evidence-search-error { margin-top: 8px; color: var(--danger); font-size: 0.8125rem; }
.evidence-search-results { display: grid; gap: 4px; margin-top: 10px; }
.evidence-search-results button { display: grid; grid-template-columns: 64px 72px minmax(0, 1fr); align-items: center; gap: 10px; padding: 8px 10px; border: 0; border-radius: var(--control-radius); background: transparent; text-align: left; font-size: 0.875rem; cursor: pointer; }
.evidence-search-results button:hover { background: var(--sheet); }
.evidence-search-results strong { justify-self: start; }
.evidence-search-results small { color: var(--ink-soft); font-size: 0.8125rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-search-results span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 分析结果正文：衬线标题 + 舒适行长 */
.markdown-content, .text-content { color: var(--ink); font-size: 1rem; line-height: 1.85; }
.markdown-content { max-width: 42em; }
.markdown-content h1, .markdown-content h2, .markdown-content h3 { font-family: var(--serif); font-weight: 900; line-height: 1.35; margin: 1.6em 0 0.6em; }
.markdown-content > :first-child { margin-top: 0; }
.markdown-content h1 { font-size: 1.75rem; }
.markdown-content h2 { font-size: 1.375rem; padding-top: 1em; border-top: 1px solid var(--rule-soft); }
.markdown-content > h2:first-child { padding-top: 0; border-top: 0; }
.markdown-content h3 { font-size: 1.125rem; font-weight: 700; }
.markdown-content p { margin-bottom: 1em; }
.markdown-content ul, .markdown-content ol { padding-left: 1.4em; margin-bottom: 1em; }
.markdown-content li { margin-bottom: 0.5em; }
.markdown-content li::marker { color: var(--ink-faint); }
.markdown-content strong { font-weight: 700; }
.markdown-content a:not([href^="#video-t="]) { color: var(--cobalt); text-underline-offset: 3px; }
.markdown-content blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid var(--rule); color: var(--ink-soft); }
.markdown-content code { padding: 0.1em 0.35em; border-radius: 4px; background: var(--paper); font-size: 0.9em; }
.markdown-content pre { margin: 1em 0; padding: 14px 16px; border-radius: 8px; background: var(--paper); overflow-x: auto; }
.markdown-content pre code { padding: 0; background: none; }
.markdown-content table { width: 100%; margin: 1em 0; border-collapse: collapse; font-size: 0.9375rem; }
.markdown-content th, .markdown-content td { padding: 8px 10px; border-bottom: 1px solid var(--rule-soft); text-align: left; vertical-align: top; }
.markdown-content th { border-bottom-color: var(--rule); font-weight: 700; }
.text-content pre { white-space: pre-wrap; padding: 20px 22px; border-radius: 10px; background: var(--paper); color: var(--ink); font-family: var(--sans); font-size: 0.9375rem; line-height: 1.9; }
.text-meta { margin-bottom: 12px; color: var(--ink-soft); font-size: 0.875rem; }

.agent-inspector { margin-top: 36px; border-top: 1px solid var(--rule); }
.agent-inspector summary { padding: 14px 0; color: var(--ink-soft); font-weight: 500; cursor: pointer; }
.agent-inspector summary:hover { color: var(--ink); }
.agent-inspector-content { padding-bottom: 8px; }
.agent-meta-block { margin-bottom: 16px; padding: 16px 18px; border-radius: 10px; background: var(--paper); }
.meta-label { display: block; margin-bottom: 8px; font-size: 0.875rem; font-weight: 700; }
.agent-meta-block ol { padding-left: 1.3em; color: var(--ink); }
.agent-meta-block li { margin: 6px 0; }
.stage-list { list-style: none; padding-left: 0 !important; }
.stage-list li { display: flex; justify-content: space-between; gap: 16px; padding: 4px 0; border-bottom: 1px dashed var(--rule); font-size: 0.875rem; }
.stage-list li:last-child { border-bottom: 0; }
.stage-time { color: var(--ink-soft); }
.plan-editor { display: grid; gap: 8px; margin-top: 4px; }
.plan-editor-row { display: grid; grid-template-columns: minmax(0, 1fr) 36px; gap: 8px; }
.plan-editor input { min-width: 0; border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 8px 10px; outline: none; }
.plan-editor input:focus { border-color: var(--cobalt); }
.plan-editor button, .plan-edit-trigger { display: inline-grid; place-items: center; border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 6px 12px; font-size: 0.875rem; cursor: pointer; }
.plan-editor button:hover, .plan-edit-trigger:hover { border-color: var(--ink); }
.plan-editor button.is-primary { border-color: var(--cobalt); background: var(--cobalt); color: var(--sheet); }
.plan-editor-row button { padding: 0; }
.plan-editor-actions { display: flex; justify-content: flex-end; gap: 8px; }
.plan-edit-trigger { margin-top: 10px; }
.quality-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.quality-row div { padding: 12px 14px; border-radius: 10px; background: var(--paper); }
.quality-row dt { color: var(--ink-soft); font-size: 0.8125rem; }
.quality-row dd { margin-top: 2px; font-weight: 700; }

.follow-up-box { display: grid; grid-template-columns: 1fr auto; gap: 10px; margin-top: 28px; }
.follow-up-box textarea { min-height: 76px; }
.follow-up-box button { min-width: 88px; border: 0; border-radius: 8px; background: var(--cobalt); color: var(--sheet); font-weight: 500; cursor: pointer; }
.follow-up-box button:hover:not(:disabled) { background: var(--cobalt-deep); }
.feedback-row { display: flex; align-items: center; gap: 8px; margin-top: 16px; color: var(--ink-soft); font-size: 0.875rem; }
.feedback-row button { border: 1px solid var(--rule); border-radius: 999px; background: var(--sheet); color: var(--ink); padding: 4px 14px; cursor: pointer; }
.feedback-row button:hover:not(:disabled) { border-color: var(--ink); }
.feedback-row button.active { border-color: var(--cobalt); background: var(--cobalt-wash); color: var(--cobalt-deep); }

/* 登录框 */
.auth-backdrop { position: fixed; inset: 0; z-index: 2000; display: flex; align-items: center; justify-content: center; padding: 16px; background: rgba(24, 32, 46, 0.45); }
.auth-panel { width: 400px; max-width: 100%; background: var(--sheet); border-radius: var(--surface-radius); box-shadow: 0 24px 64px -24px rgba(24, 32, 46, 0.5); animation: rise 0.22s ease-out; }
.auth-header { display: flex; align-items: center; justify-content: space-between; padding: 22px 24px 4px 28px; }
.auth-title { font-family: var(--serif); font-size: 1.375rem; font-weight: 900; }
.auth-body { padding: 16px 28px 28px; }
.input-group { margin-bottom: 18px; }
.input-group label { display: block; margin-bottom: 6px; font-size: 0.875rem; font-weight: 500; }
.input-group input { width: 100%; border: 1px solid var(--rule); border-radius: var(--control-radius); background: var(--sheet); color: var(--ink); padding: 10px 12px; font-size: 1rem; outline: none; }
.input-group input:focus { border-color: var(--cobalt); box-shadow: 0 0 0 3px var(--cobalt-wash); }
.primary-btn { width: 100%; margin-top: 4px; border: 0; border-radius: var(--control-radius); background: var(--cobalt); color: var(--sheet); padding: 11px; font-size: 1rem; font-weight: 500; cursor: pointer; }
.primary-btn:hover:not(:disabled) { background: var(--cobalt-deep); }
.primary-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.auth-toggle { margin-top: 16px; text-align: center; color: var(--ink-soft); font-size: 0.875rem; }
.toggle-link { margin-left: 4px; border: 0; background: none; color: var(--cobalt); font-weight: 500; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }
.auth-msg { margin-top: 14px; text-align: center; color: var(--ok); font-size: 0.875rem; }
.auth-msg.error { color: var(--danger); }

@media (max-width: 960px) {
  .showcase-frame { grid-template-columns: 1fr; }
  .agent-mode-row { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .masthead-inner { padding: 10px 16px; }
  .wordmark { font-size: 1.25rem; }
  .user-name { display: none; }
  .main-container { padding: 48px 16px 72px; }
  .main-container.is-app { padding-top: 20px; }
  .hero { margin-bottom: 48px; }
  .hero-lede { font-size: 1.0625rem; }
  .composer { margin-top: 28px; }
  .composer-link { padding-left: 12px; }
  .composer-link input { height: 44px; font-size: 1rem; }
  .composer-go { height: 40px; padding: 0 16px; }
  .composer.is-compact { grid-template-columns: 1fr; }
  .composer.is-compact .composer-or { display: none; }
  .composer.is-compact .composer-file { height: 44px; }
  .showcase-slide { min-height: 220px; }
  .showcase-notes { padding: 22px 20px; }
  .section-header { flex-direction: column; align-items: stretch; gap: 12px; }
  .library-search { width: 100%; }
  .media-grid { grid-template-columns: 1fr; gap: 28px; }
  .dock-item { flex: 1 1 auto; min-height: 36px; }
  .sidebar-panel { width: 100%; max-width: 100vw; }
  .sidebar-header { padding: 12px 16px; }
  .sidebar-body { padding: 20px 16px 40px; }
  .video-evidence { margin: -20px -16px 22px; }
  .video-evidence p, .video-evidence-loading { padding-left: 16px; padding-right: 16px; }
  .agent-mode-row, .preset-row { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .goal-presets button { min-height: 64px; }
  .agent-run-btn { align-self: stretch; }
  .result-actions { justify-content: stretch; }
  .result-actions button { flex: 1; padding: 8px 4px; font-size: 0.8125rem; }
  .evidence-search-form { grid-template-columns: 1fr; }
  .evidence-search-form button { min-height: 40px; }
  .evidence-search-results button { grid-template-columns: 64px minmax(0, 1fr); }
  .evidence-search-results small { display: none; }
  .quality-row { grid-template-columns: 1fr; }
  .follow-up-box { grid-template-columns: 1fr; }
  .follow-up-box button { min-height: 44px; }
  .upload-resume { flex-direction: column; align-items: stretch; }
  .upload-resume-actions button { flex: 1; min-height: 40px; }
  /*
    移动端侧栏铺满整屏，背景还能滚动会非常割裂，这里锁住。
    只在窄屏生效：移动端滚动条是浮层式的，隐藏溢出不会让内容横向跳动；
    桌面端滚动条占布局宽度，锁滚动会带来 15px 左右的位移，所以不动。
  */
  body.overlay-open { overflow: hidden; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.35; } }
@keyframes rise { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
</style>
