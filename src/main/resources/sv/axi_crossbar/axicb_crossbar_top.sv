// distributed under the mit license
// https://opensource.org/licenses/mit-license.php

///////////////////////////////////////////////////////////////////////////////
//
// AXI4 crossbar top level, instanciating the global infrastructure of the
// core. All the master and slave interfaces are instanciated here along the
// switching logic.
//
///////////////////////////////////////////////////////////////////////////////




`include "axicb_checker.sv"

/* verilator lint_off WIDTHTRUNC */
/* verilator lint_off WIDTHEXPAND */

module axicb_crossbar_top

    #(
        ///////////////////////////////////////////////////////////////////////
        // Global configuration
        ///////////////////////////////////////////////////////////////////////

        // Address width in bits
        parameter AXI_ADDR_W = 8,
        // ID width in bits
        parameter AXI_ID_W = 8,
        // Data width in bits
        parameter AXI_DATA_W = 8,

        // Number of master(s)
        parameter MST_NB = 4,
        // Number of slave(s)
        parameter SLV_NB = 4,

        // Switching logic pipelining (0 deactivate, 1 enable)
        parameter MST_PIPELINE = 0,
        parameter SLV_PIPELINE = 0,

        // AXI Signals Supported:
        //   - 0: AXI4-lite
        //   - 1: AXI
        parameter AXI_SIGNALING = 0,

        // USER fields transport enabling (0 deactivate, 1 activate)
        parameter USER_SUPPORT = 0,
        // USER fields width in bits
        parameter AXI_AUSER_W = 1,
        parameter AXI_WUSER_W = 1,
        parameter AXI_BUSER_W = 1,
        parameter AXI_RUSER_W = 1,

        // Timeout configuration in clock cycles, applied to all channels
        parameter TIMEOUT_VALUE = 10000,
        // Activate the timer to avoid deadlock
        parameter TIMEOUT_ENABLE = 1,


        ///////////////////////////////////////////////////////////////////////
        //
        // Master agent configurations:
        //
        //   - MSTx_CDC: implement input CDC stage, 0 or 1
        //
        //   - MSTx_OSTDREQ_NUM: maximum number of requests a master can
        //                       store internally
        //
        //   - MSTx_OSTDREQ_SIZE: size of an outstanding request in dataphase
        //
        //   - MSTx_PRIORITY: priority applied to this master in the arbitrers,
        //                    from 0 to 3 included
        //   - MSTx_ROUTES: routing from the master to the slaves allowed in
        //                  the switching logic. Bit 0 for slave 0, bit 1 for
        //                  slave 1, ...
        //
        //   - MSTx_ID_MASK : A mask applied in slave completion channel to
        //                    determine which master to route back the
        //                    BRESP/RRESP completions.
        //
        //   - MSTx_RW: Slect if the interface is
        //         - Read/Write (=0)
        //         - Read-only (=1)
        //         - Write-only (=2)
        //
        // The size of a master's internal buffer is equal to:
        //
        // SIZE = AXI_DATA_W * MSTx_OSTDREQ_NUM * MSTx_OSTDREQ_SIZE (in bits)
        //
        ///////////////////////////////////////////////////////////////////////


        ///////////////////////////////////////////////////////////////////////
        // Master 0 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter MST0_CDC = 0,
        parameter MST0_OSTDREQ_NUM = 4,
        parameter MST0_OSTDREQ_SIZE = 1,
        parameter MST0_PRIORITY = 0,
        parameter [SLV_NB-1:0] MST0_ROUTES = 4'b1_1_1_1,
        parameter [AXI_ID_W-1:0] MST0_ID_MASK = 'h10,
        parameter MST0_RW = 0,

        ///////////////////////////////////////////////////////////////////////
        // Master 1 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter MST1_CDC = 0,
        parameter MST1_OSTDREQ_NUM = 4,
        parameter MST1_OSTDREQ_SIZE = 1,
        parameter MST1_PRIORITY = 0,
        parameter [SLV_NB-1:0] MST1_ROUTES = 4'b1_1_1_1,
        parameter [AXI_ID_W-1:0] MST1_ID_MASK = 'h20,
        parameter MST1_RW = 0,

        ///////////////////////////////////////////////////////////////////////
        // Master 2 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter MST2_CDC = 0,
        parameter MST2_OSTDREQ_NUM = 4,
        parameter MST2_OSTDREQ_SIZE = 1,
        parameter MST2_PRIORITY = 0,
        parameter [SLV_NB-1:0] MST2_ROUTES = 4'b1_1_1_1,
        parameter [AXI_ID_W-1:0] MST2_ID_MASK = 'h30,
        parameter MST2_RW = 0,

        ///////////////////////////////////////////////////////////////////////
        // Master 3 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter MST3_CDC = 0,
        parameter MST3_OSTDREQ_NUM = 4,
        parameter MST3_OSTDREQ_SIZE = 1,
        parameter MST3_PRIORITY = 0,
        parameter [SLV_NB-1:0] MST3_ROUTES = 4'b1_1_1_1,
        parameter [AXI_ID_W-1:0] MST3_ID_MASK = 'h40,
        parameter MST3_RW = 0,


        ///////////////////////////////////////////////////////////////////////
        //
        // Slave agent configurations:
        //
        //   - SLVx_CDC: implement input CDC stage, 0 or 1
        //
        //   - SLVx_OSTDREQ_NUM: maximum number of requests slave can
        //                       store internally
        //
        //   - SLVx_OSTDREQ_SIZE: size of an outstanding request in dataphase
        //
        //   - SLVx_START_ADDR: Start address allocated to the slave, in byte
        //
        //   - SLVx_END_ADDR: End address allocated to the slave, in byte
        //
        //   - SLVx_KEEP_BASE_ADDR: Keep the absolute address of the slave in
        //     the memory map. Default to 0.
        //
        // The size of a slave's internal buffer is equal to:
        //
        //   AXI_DATA_W * SLVx_OSTDREQ_NUM * SLVx_OSTDREQ_SIZE (in bits)
        //
        // A request is routed to a slave if:
        //
        //   START_ADDR <= ADDR <= END_ADDR
        //
        ///////////////////////////////////////////////////////////////////////


        ///////////////////////////////////////////////////////////////////////
        // Slave 0 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter SLV0_CDC = 0,
        parameter SLV0_START_ADDR = 0,
        parameter SLV0_END_ADDR = 4095,
        parameter SLV0_OSTDREQ_NUM = 4,
        parameter SLV0_OSTDREQ_SIZE = 1,
        parameter SLV0_KEEP_BASE_ADDR = 1,

        ///////////////////////////////////////////////////////////////////////
        // Slave 1 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter SLV1_CDC = 0,
        parameter SLV1_START_ADDR = 4096,
        parameter SLV1_END_ADDR = 8191,
        parameter SLV1_OSTDREQ_NUM = 4,
        parameter SLV1_OSTDREQ_SIZE = 1,
        parameter SLV1_KEEP_BASE_ADDR = 1,

        ///////////////////////////////////////////////////////////////////////
        // Slave 2 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter SLV2_CDC = 0,
        parameter SLV2_START_ADDR = 8192,
        parameter SLV2_END_ADDR = 12287,
        parameter SLV2_OSTDREQ_NUM = 4,
        parameter SLV2_OSTDREQ_SIZE = 1,
        parameter SLV2_KEEP_BASE_ADDR = 0,

        ///////////////////////////////////////////////////////////////////////
        // Slave 3 configuration
        ///////////////////////////////////////////////////////////////////////

        parameter SLV3_CDC = 0,
        parameter SLV3_START_ADDR = 12288,
        parameter SLV3_END_ADDR = 16383,
        parameter SLV3_OSTDREQ_NUM = 4,
        parameter SLV3_OSTDREQ_SIZE = 1,
        parameter SLV3_KEEP_BASE_ADDR = 0
    )(
        ///////////////////////////////////////////////////////////////////////
        // Interconnect global interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       aclk,
        input  wire                       aresetn,
        input  wire                       srst,

        ///////////////////////////////////////////////////////////////////////
        // Master Agent 0 interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       slv_clk_0_aclk,
        input  wire                       slv_clk_0_aresetn,
        input  wire                       slv_clk_0_srst,
        input  wire                       slv_0_aw_valid,
        output logic                      slv_0_aw_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_0_aw_addr,
        input  wire  [8             -1:0] slv_0_aw_len,
        input  wire  [3             -1:0] slv_0_aw_size,
        input  wire  [2             -1:0] slv_0_aw_burst,
        input  wire                       slv_0_aw_lock,
        input  wire  [4             -1:0] slv_0_aw_cache,
        input  wire  [3             -1:0] slv_0_aw_prot,
        input  wire  [4             -1:0] slv_0_aw_qos,
        input  wire  [4             -1:0] slv_0_aw_region,
        input  wire  [AXI_ID_W      -1:0] slv_0_aw_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_0_aw_user,
        input  wire                       slv_0_w_valid,
        output logic                      slv_0_w_ready,
        input  wire                       slv_0_w_last,
        input  wire  [AXI_DATA_W    -1:0] slv_0_w_data,
        input  wire  [AXI_DATA_W/8  -1:0] slv_0_w_strb,
        input  wire  [AXI_WUSER_W   -1:0] slv_0_w_user,
        output logic                      slv_0_b_valid,
        input  wire                       slv_0_b_ready,
        output logic [AXI_ID_W      -1:0] slv_0_b_id,
        output logic [2             -1:0] slv_0_b_resp,
        output logic [AXI_BUSER_W   -1:0] slv_0_b_user,
        input  wire                       slv_0_ar_valid,
        output logic                      slv_0_ar_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_0_ar_addr,
        input  wire  [8             -1:0] slv_0_ar_len,
        input  wire  [3             -1:0] slv_0_ar_size,
        input  wire  [2             -1:0] slv_0_ar_burst,
        input  wire                       slv_0_ar_lock,
        input  wire  [4             -1:0] slv_0_ar_cache,
        input  wire  [3             -1:0] slv_0_ar_prot,
        input  wire  [4             -1:0] slv_0_ar_qos,
        input  wire  [4             -1:0] slv_0_ar_region,
        input  wire  [AXI_ID_W      -1:0] slv_0_ar_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_0_ar_user,
        output logic                      slv_0_r_valid,
        input  wire                       slv_0_r_ready,
        output logic [AXI_ID_W      -1:0] slv_0_r_id,
        output logic [2             -1:0] slv_0_r_resp,
        output logic [AXI_DATA_W    -1:0] slv_0_r_data,
        output logic                      slv_0_r_last,
        output logic [AXI_RUSER_W   -1:0] slv_0_r_user,

        /////////////////////////////////////_//////////////////////////////////
        // Master Agent 1 interface
        /////////////////////////////////////_//////////////////////////////////

        input  wire                       slv_clk_1_aclk,
        input  wire                       slv_clk_1_aresetn,
        input  wire                       slv_clk_1_srst,
        input  wire                       slv_1_aw_valid,
        output logic                      slv_1_aw_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_1_aw_addr,
        input  wire  [8             -1:0] slv_1_aw_len,
        input  wire  [3             -1:0] slv_1_aw_size,
        input  wire  [2             -1:0] slv_1_aw_burst,
        input  wire                       slv_1_aw_lock,
        input  wire  [4             -1:0] slv_1_aw_cache,
        input  wire  [3             -1:0] slv_1_aw_prot,
        input  wire  [4             -1:0] slv_1_aw_qos,
        input  wire  [4             -1:0] slv_1_aw_region,
        input  wire  [AXI_ID_W      -1:0] slv_1_aw_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_1_aw_user,
        input  wire                       slv_1_w_valid,
        output logic                      slv_1_w_ready,
        input  wire                       slv_1_w_last,
        input  wire  [AXI_DATA_W    -1:0] slv_1_w_data,
        input  wire  [AXI_DATA_W/8  -1:0] slv_1_w_strb,
        input  wire  [AXI_WUSER_W   -1:0] slv_1_w_user,
        output logic                      slv_1_b_valid,
        input  wire                       slv_1_b_ready,
        output logic [AXI_ID_W      -1:0] slv_1_b_id,
        output logic [2             -1:0] slv_1_b_resp,
        output logic [AXI_BUSER_W   -1:0] slv_1_b_user,
        input  wire                       slv_1_ar_valid,
        output logic                      slv_1_ar_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_1_ar_addr,
        input  wire  [8             -1:0] slv_1_ar_len,
        input  wire  [3             -1:0] slv_1_ar_size,
        input  wire  [2             -1:0] slv_1_ar_burst,
        input  wire                       slv_1_ar_lock,
        input  wire  [4             -1:0] slv_1_ar_cache,
        input  wire  [3             -1:0] slv_1_ar_prot,
        input  wire  [4             -1:0] slv_1_ar_qos,
        input  wire  [4             -1:0] slv_1_ar_region,
        input  wire  [AXI_ID_W      -1:0] slv_1_ar_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_1_ar_user,
        output logic                      slv_1_r_valid,
        input  wire                       slv_1_r_ready,
        output logic [AXI_ID_W      -1:0] slv_1_r_id,
        output logic [2             -1:0] slv_1_r_resp,
        output logic [AXI_DATA_W    -1:0] slv_1_r_data,
        output logic                      slv_1_r_last,
        output logic [AXI_RUSER_W   -1:0] slv_1_r_user,

        /////////////////////////////////////_//////////////////////////////////
        // Master Agent 2 interface_
        /////////////////////////////////////_//////////////////////////////////

        input  wire                       slv_clk_2_aclk,
        input  wire                       slv_clk_2_aresetn,
        input  wire                       slv_clk_2_srst,
        input  wire                       slv_2_aw_valid,
        output logic                      slv_2_aw_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_2_aw_addr,
        input  wire  [8             -1:0] slv_2_aw_len,
        input  wire  [3             -1:0] slv_2_aw_size,
        input  wire  [2             -1:0] slv_2_aw_burst,
        input  wire                       slv_2_aw_lock,
        input  wire  [4             -1:0] slv_2_aw_cache,
        input  wire  [3             -1:0] slv_2_aw_prot,
        input  wire  [4             -1:0] slv_2_aw_qos,
        input  wire  [4             -1:0] slv_2_aw_region,
        input  wire  [AXI_ID_W      -1:0] slv_2_aw_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_2_aw_user,
        input  wire                       slv_2_w_valid,
        output logic                      slv_2_w_ready,
        input  wire                       slv_2_w_last,
        input  wire  [AXI_DATA_W    -1:0] slv_2_w_data,
        input  wire  [AXI_DATA_W/8  -1:0] slv_2_w_strb,
        input  wire  [AXI_WUSER_W   -1:0] slv_2_w_user,
        output logic                      slv_2_b_valid,
        input  wire                       slv_2_b_ready,
        output logic [AXI_ID_W      -1:0] slv_2_b_id,
        output logic [2             -1:0] slv_2_b_resp,
        output logic [AXI_BUSER_W   -1:0] slv_2_b_user,
        input  wire                       slv_2_ar_valid,
        output logic                      slv_2_ar_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_2_ar_addr,
        input  wire  [8             -1:0] slv_2_ar_len,
        input  wire  [3             -1:0] slv_2_ar_size,
        input  wire  [2             -1:0] slv_2_ar_burst,
        input  wire                       slv_2_ar_lock,
        input  wire  [4             -1:0] slv_2_ar_cache,
        input  wire  [3             -1:0] slv_2_ar_prot,
        input  wire  [4             -1:0] slv_2_ar_qos,
        input  wire  [4             -1:0] slv_2_ar_region,
        input  wire  [AXI_ID_W      -1:0] slv_2_ar_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_2_ar_user,
        output logic                      slv_2_r_valid,
        input  wire                       slv_2_r_ready,
        output logic [AXI_ID_W      -1:0] slv_2_r_id,
        output logic [2             -1:0] slv_2_r_resp,
        output logic [AXI_DATA_W    -1:0] slv_2_r_data,
        output logic                      slv_2_r_last,
        output logic [AXI_RUSER_W   -1:0] slv_2_r_user,

        /////////////////////////////////////_//////////////////////////////////
        // Master Agent 3 interface_
        /////////////////////////////////////_//////////////////////////////////

        input  wire                       slv_clk_3_aclk,
        input  wire                       slv_clk_3_aresetn,
        input  wire                       slv_clk_3_srst,
        input  wire                       slv_3_aw_valid,
        output logic                      slv_3_aw_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_3_aw_addr,
        input  wire  [8             -1:0] slv_3_aw_len,
        input  wire  [3             -1:0] slv_3_aw_size,
        input  wire  [2             -1:0] slv_3_aw_burst,
        input  wire                       slv_3_aw_lock,
        input  wire  [4             -1:0] slv_3_aw_cache,
        input  wire  [3             -1:0] slv_3_aw_prot,
        input  wire  [4             -1:0] slv_3_aw_qos,
        input  wire  [4             -1:0] slv_3_aw_region,
        input  wire  [AXI_ID_W      -1:0] slv_3_aw_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_3_aw_user,
        input  wire                       slv_3_w_valid,
        output logic                      slv_3_w_ready,
        input  wire                       slv_3_w_last,
        input  wire  [AXI_DATA_W    -1:0] slv_3_w_data,
        input  wire  [AXI_DATA_W/8  -1:0] slv_3_w_strb,
        input  wire  [AXI_WUSER_W   -1:0] slv_3_w_user,
        output logic                      slv_3_b_valid,
        input  wire                       slv_3_b_ready,
        output logic [AXI_ID_W      -1:0] slv_3_b_id,
        output logic [2             -1:0] slv_3_b_resp,
        output logic [AXI_BUSER_W   -1:0] slv_3_b_user,
        input  wire                       slv_3_ar_valid,
        output logic                      slv_3_ar_ready,
        input  wire  [AXI_ADDR_W    -1:0] slv_3_ar_addr,
        input  wire  [8             -1:0] slv_3_ar_len,
        input  wire  [3             -1:0] slv_3_ar_size,
        input  wire  [2             -1:0] slv_3_ar_burst,
        input  wire                       slv_3_ar_lock,
        input  wire  [4             -1:0] slv_3_ar_cache,
        input  wire  [3             -1:0] slv_3_ar_prot,
        input  wire  [4             -1:0] slv_3_ar_qos,
        input  wire  [4             -1:0] slv_3_ar_region,
        input  wire  [AXI_ID_W      -1:0] slv_3_ar_id,
        input  wire  [AXI_AUSER_W   -1:0] slv_3_ar_user,
        output logic                      slv_3_r_valid,
        input  wire                       slv_3_r_ready,
        output logic [AXI_ID_W      -1:0] slv_3_r_id,
        output logic [2             -1:0] slv_3_r_resp,
        output logic [AXI_DATA_W    -1:0] slv_3_r_data,
        output logic                      slv_3_r_last,
        output logic [AXI_RUSER_W   -1:0] slv_3_r_user,

        ///////////////////////////////////////////////////////////////////////
        // Slave Agent 0 interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       mst_clk_0_aclk,
        input  wire                       mst_clk_0_aresetn,
        input  wire                       mst_clk_0_srst,
        output logic                      mst_0_aw_valid,
        input  wire                       mst_0_aw_ready,
        output logic [AXI_ADDR_W    -1:0] mst_0_aw_addr,
        output logic [8             -1:0] mst_0_aw_len,
        output logic [3             -1:0] mst_0_aw_size,
        output logic [2             -1:0] mst_0_aw_burst,
        output logic                      mst_0_aw_lock,
        output logic [4             -1:0] mst_0_aw_cache,
        output logic [3             -1:0] mst_0_aw_prot,
        output logic [4             -1:0] mst_0_aw_qos,
        output logic [4             -1:0] mst_0_aw_region,
        output logic [AXI_ID_W      -1:0] mst_0_aw_id,
        output logic [AXI_AUSER_W   -1:0] mst_0_aw_user,
        output logic                      mst_0_w_valid,
        input  wire                       mst_0_w_ready,
        output logic                      mst_0_w_last,
        output logic [AXI_DATA_W    -1:0] mst_0_w_data,
        output logic [AXI_DATA_W/8  -1:0] mst_0_w_strb,
        output logic [AXI_WUSER_W   -1:0] mst_0_w_user,
        input  wire                       mst_0_b_valid,
        output logic                      mst_0_b_ready,
        input  wire  [AXI_ID_W      -1:0] mst_0_b_id,
        input  wire  [2             -1:0] mst_0_b_resp,
        input  wire  [AXI_BUSER_W   -1:0] mst_0_b_user,
        output logic                      mst_0_ar_valid,
        input  wire                       mst_0_ar_ready,
        output logic [AXI_ADDR_W    -1:0] mst_0_ar_addr,
        output logic [8             -1:0] mst_0_ar_len,
        output logic [3             -1:0] mst_0_ar_size,
        output logic [2             -1:0] mst_0_ar_burst,
        output logic                      mst_0_ar_lock,
        output logic [4             -1:0] mst_0_ar_cache,
        output logic [3             -1:0] mst_0_ar_prot,
        output logic [4             -1:0] mst_0_ar_qos,
        output logic [4             -1:0] mst_0_ar_region,
        output logic [AXI_ID_W      -1:0] mst_0_ar_id,
        output logic [AXI_AUSER_W   -1:0] mst_0_ar_user,
        input  wire                       mst_0_r_valid,
        output logic                      mst_0_r_ready,
        input  wire  [AXI_ID_W      -1:0] mst_0_r_id,
        input  wire  [2             -1:0] mst_0_r_resp,
        input  wire  [AXI_DATA_W    -1:0] mst_0_r_data,
        input  wire                       mst_0_r_last,
        input  wire  [AXI_RUSER_W   -1:0] mst_0_r_user,

        ///////////////////////////////////////////////////////////////////////
        // Slave Agent 1 interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       mst_clk_1_aclk,
        input  wire                       mst_clk_1_aresetn,
        input  wire                       mst_clk_1_srst,
        output logic                      mst_1_aw_valid,
        input  wire                       mst_1_aw_ready,
        output logic [AXI_ADDR_W    -1:0] mst_1_aw_addr,
        output logic [8             -1:0] mst_1_aw_len,
        output logic [3             -1:0] mst_1_aw_size,
        output logic [2             -1:0] mst_1_aw_burst,
        output logic                      mst_1_aw_lock,
        output logic [4             -1:0] mst_1_aw_cache,
        output logic [3             -1:0] mst_1_aw_prot,
        output logic [4             -1:0] mst_1_aw_qos,
        output logic [4             -1:0] mst_1_aw_region,
        output logic [AXI_ID_W      -1:0] mst_1_aw_id,
        output logic [AXI_AUSER_W   -1:0] mst_1_aw_user,
        output logic                      mst_1_w_valid,
        input  wire                       mst_1_w_ready,
        output logic                      mst_1_w_last,
        output logic [AXI_DATA_W    -1:0] mst_1_w_data,
        output logic [AXI_DATA_W/8  -1:0] mst_1_w_strb,
        output logic [AXI_WUSER_W   -1:0] mst_1_w_user,
        input  wire                       mst_1_b_valid,
        output logic                      mst_1_b_ready,
        input  wire  [AXI_ID_W      -1:0] mst_1_b_id,
        input  wire  [2             -1:0] mst_1_b_resp,
        input  wire  [AXI_BUSER_W   -1:0] mst_1_b_user,
        output logic                      mst_1_ar_valid,
        input  wire                       mst_1_ar_ready,
        output logic [AXI_ADDR_W    -1:0] mst_1_ar_addr,
        output logic [8             -1:0] mst_1_ar_len,
        output logic [3             -1:0] mst_1_ar_size,
        output logic [2             -1:0] mst_1_ar_burst,
        output logic                      mst_1_ar_lock,
        output logic [4             -1:0] mst_1_ar_cache,
        output logic [3             -1:0] mst_1_ar_prot,
        output logic [4             -1:0] mst_1_ar_qos,
        output logic [4             -1:0] mst_1_ar_region,
        output logic [AXI_ID_W      -1:0] mst_1_ar_id,
        output logic [AXI_AUSER_W   -1:0] mst_1_ar_user,
        input  wire                       mst_1_r_valid,
        output logic                      mst_1_r_ready,
        input  wire  [AXI_ID_W      -1:0] mst_1_r_id,
        input  wire  [2             -1:0] mst_1_r_resp,
        input  wire  [AXI_DATA_W    -1:0] mst_1_r_data,
        input  wire                       mst_1_r_last,
        input  wire  [AXI_RUSER_W   -1:0] mst_1_r_user,

        ///////////////////////////////////////////////////////////////////////
        // Slave Agent 2 interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       mst_clk_2_aclk,
        input  wire                       mst_clk_2_aresetn,
        input  wire                       mst_clk_2_srst,
        output logic                      mst_2_aw_valid,
        input  wire                       mst_2_aw_ready,
        output logic [AXI_ADDR_W    -1:0] mst_2_aw_addr,
        output logic [8             -1:0] mst_2_aw_len,
        output logic [3             -1:0] mst_2_aw_size,
        output logic [2             -1:0] mst_2_aw_burst,
        output logic                      mst_2_aw_lock,
        output logic [4             -1:0] mst_2_aw_cache,
        output logic [3             -1:0] mst_2_aw_prot,
        output logic [4             -1:0] mst_2_aw_qos,
        output logic [4             -1:0] mst_2_aw_region,
        output logic [AXI_ID_W      -1:0] mst_2_aw_id,
        output logic [AXI_AUSER_W   -1:0] mst_2_aw_user,
        output logic                      mst_2_w_valid,
        input  wire                       mst_2_w_ready,
        output logic                      mst_2_w_last,
        output logic [AXI_DATA_W    -1:0] mst_2_w_data,
        output logic [AXI_DATA_W/8  -1:0] mst_2_w_strb,
        output logic [AXI_WUSER_W   -1:0] mst_2_w_user,
        input  wire                       mst_2_b_valid,
        output logic                      mst_2_b_ready,
        input  wire  [AXI_ID_W      -1:0] mst_2_b_id,
        input  wire  [2             -1:0] mst_2_b_resp,
        input  wire  [AXI_BUSER_W   -1:0] mst_2_b_user,
        output logic                      mst_2_ar_valid,
        input  wire                       mst_2_ar_ready,
        output logic [AXI_ADDR_W    -1:0] mst_2_ar_addr,
        output logic [8             -1:0] mst_2_ar_len,
        output logic [3             -1:0] mst_2_ar_size,
        output logic [2             -1:0] mst_2_ar_burst,
        output logic                      mst_2_ar_lock,
        output logic [4             -1:0] mst_2_ar_cache,
        output logic [3             -1:0] mst_2_ar_prot,
        output logic [4             -1:0] mst_2_ar_qos,
        output logic [4             -1:0] mst_2_ar_region,
        output logic [AXI_ID_W      -1:0] mst_2_ar_id,
        output logic [AXI_AUSER_W   -1:0] mst_2_ar_user,
        input  wire                       mst_2_r_valid,
        output logic                      mst_2_r_ready,
        input  wire  [AXI_ID_W      -1:0] mst_2_r_id,
        input  wire  [2             -1:0] mst_2_r_resp,
        input  wire  [AXI_DATA_W    -1:0] mst_2_r_data,
        input  wire                       mst_2_r_last,
        input  wire  [AXI_RUSER_W   -1:0] mst_2_r_user,

        ///////////////////////////////////////////////////////////////////////
        // Slave Agent 3 interface
        ///////////////////////////////////////////////////////////////////////

        input  wire                       mst_clk_3_aclk,
        input  wire                       mst_clk_3_aresetn,
        input  wire                       mst_clk_3_srst,
        output logic                      mst_3_aw_valid,
        input  wire                       mst_3_aw_ready,
        output logic [AXI_ADDR_W    -1:0] mst_3_aw_addr,
        output logic [8             -1:0] mst_3_aw_len,
        output logic [3             -1:0] mst_3_aw_size,
        output logic [2             -1:0] mst_3_aw_burst,
        output logic                      mst_3_aw_lock,
        output logic [4             -1:0] mst_3_aw_cache,
        output logic [3             -1:0] mst_3_aw_prot,
        output logic [4             -1:0] mst_3_aw_qos,
        output logic [4             -1:0] mst_3_aw_region,
        output logic [AXI_ID_W      -1:0] mst_3_aw_id,
        output logic [AXI_AUSER_W   -1:0] mst_3_aw_user,
        output logic                      mst_3_w_valid,
        input  wire                       mst_3_w_ready,
        output logic                      mst_3_w_last,
        output logic [AXI_DATA_W    -1:0] mst_3_w_data,
        output logic [AXI_DATA_W/8  -1:0] mst_3_w_strb,
        output logic [AXI_WUSER_W   -1:0] mst_3_w_user,
        input  wire                       mst_3_b_valid,
        output logic                      mst_3_b_ready,
        input  wire  [AXI_ID_W      -1:0] mst_3_b_id,
        input  wire  [2             -1:0] mst_3_b_resp,
        input  wire  [AXI_BUSER_W   -1:0] mst_3_b_user,
        output logic                      mst_3_ar_valid,
        input  wire                       mst_3_ar_ready,
        output logic [AXI_ADDR_W    -1:0] mst_3_ar_addr,
        output logic [8             -1:0] mst_3_ar_len,
        output logic [3             -1:0] mst_3_ar_size,
        output logic [2             -1:0] mst_3_ar_burst,
        output logic                      mst_3_ar_lock,
        output logic [4             -1:0] mst_3_ar_cache,
        output logic [3             -1:0] mst_3_ar_prot,
        output logic [4             -1:0] mst_3_ar_qos,
        output logic [4             -1:0] mst_3_ar_region,
        output logic [AXI_ID_W      -1:0] mst_3_ar_id,
        output logic [AXI_AUSER_W   -1:0] mst_3_ar_user,
        input  wire                       mst_3_r_valid,
        output logic                      mst_3_r_ready,
        input  wire  [AXI_ID_W      -1:0] mst_3_r_id,
        input  wire  [2             -1:0] mst_3_r_resp,
        input  wire  [AXI_DATA_W    -1:0] mst_3_r_data,
        input  wire                       mst_3_r_last,
        input  wire  [AXI_RUSER_W   -1:0] mst_3_r_user
    );


    ///////////////////////////////////////////////////////////////////////////
    // Parameters setup checks
    ///////////////////////////////////////////////////////////////////////////

    initial begin

        `CHECKER((MST0_OSTDREQ_NUM>0 && MST0_OSTDREQ_SIZE==0),
            "MST0 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((MST1_OSTDREQ_NUM>0 && MST1_OSTDREQ_SIZE==0),
            "MST1 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((MST2_OSTDREQ_NUM>0 && MST2_OSTDREQ_SIZE==0),
            "MST2 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((MST3_OSTDREQ_NUM>0 && MST3_OSTDREQ_SIZE==0),
            "MST3 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((SLV0_OSTDREQ_NUM>0 && SLV0_OSTDREQ_SIZE==0),
            "SLV0 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((SLV1_OSTDREQ_NUM>0 && SLV1_OSTDREQ_SIZE==0),
            "SLV1 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((SLV2_OSTDREQ_NUM>0 && SLV2_OSTDREQ_SIZE==0),
            "SLV2 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((SLV3_OSTDREQ_NUM>0 && SLV3_OSTDREQ_SIZE==0),
            "SLV3 is setup with oustanding request but their size must be greater than 0");

        `CHECKER((MST0_ID_MASK==0), "MST0 mask ID must be greater than 0");

        `CHECKER((MST1_ID_MASK==0), "MST1 mask ID must be greater than 0");

        `CHECKER((MST2_ID_MASK==0), "MST2 mask ID must be greater than 0");

        `CHECKER((MST3_ID_MASK==0), "MST3 mask ID must be greater than 0");

    end


    ///////////////////////////////////////////////////////////////////////////
    // Local declarations
    ///////////////////////////////////////////////////////////////////////////

    localparam AUSER_W = (USER_SUPPORT > 0) ? AXI_AUSER_W : 0;

    localparam WUSER_W = (USER_SUPPORT > 0) ? AXI_WUSER_W : 0;

    localparam BUSER_W = (USER_SUPPORT > 0) ? AXI_BUSER_W : 0;

    localparam RUSER_W = (USER_SUPPORT > 0) ? AXI_RUSER_W : 0;

                                             // AXI4-lite signaling
    localparam AWCH_W = (AXI_SIGNALING==0) ? AXI_ADDR_W + AXI_ID_W + 3 + AUSER_W :
                                             // AXI4 signaling
                                             AXI_ADDR_W + AXI_ID_W + 29 + AUSER_W;

    localparam WCH_W = AXI_DATA_W + AXI_DATA_W/8 + WUSER_W;

    localparam BCH_W = AXI_ID_W + 2 + BUSER_W;

    localparam ARCH_W = AWCH_W;

    localparam RCH_W = AXI_DATA_W + AXI_ID_W + 2 + RUSER_W;

    localparam MST_ROUTES = {MST3_ROUTES,
                             MST2_ROUTES,
                             MST1_ROUTES,
                             MST0_ROUTES};

    logic [MST_NB            -1:0] i_awvalid;
    logic [MST_NB            -1:0] i_awready;
    logic [MST_NB*AWCH_W     -1:0] i_awch;
    logic [MST_NB            -1:0] i_wvalid;
    logic [MST_NB            -1:0] i_wready;
    logic [MST_NB            -1:0] i_wlast;
    logic [MST_NB*WCH_W      -1:0] i_wch;
    logic [MST_NB            -1:0] i_bvalid;
    logic [MST_NB            -1:0] i_bready;
    logic [MST_NB*BCH_W      -1:0] i_bch;
    logic [MST_NB            -1:0] i_arvalid;
    logic [MST_NB            -1:0] i_arready;
    logic [MST_NB*ARCH_W     -1:0] i_arch;
    logic [MST_NB            -1:0] i_rvalid;
    logic [MST_NB            -1:0] i_rready;
    logic [MST_NB            -1:0] i_rlast;
    logic [MST_NB*RCH_W      -1:0] i_rch;
    logic [SLV_NB            -1:0] o_awvalid;
    logic [SLV_NB            -1:0] o_awready;
    logic [SLV_NB*AWCH_W     -1:0] o_awch;
    logic [SLV_NB            -1:0] o_wvalid;
    logic [SLV_NB            -1:0] o_wready;
    logic [SLV_NB            -1:0] o_wlast;
    logic [SLV_NB*WCH_W      -1:0] o_wch;
    logic [SLV_NB            -1:0] o_bvalid;
    logic [SLV_NB            -1:0] o_bready;
    logic [SLV_NB*BCH_W      -1:0] o_bch;
    logic [SLV_NB            -1:0] o_arvalid;
    logic [SLV_NB            -1:0] o_arready;
    logic [SLV_NB*ARCH_W     -1:0] o_arch;
    logic [SLV_NB            -1:0] o_rvalid;
    logic [SLV_NB            -1:0] o_rready;
    logic [SLV_NB            -1:0] o_rlast;
    logic [SLV_NB*RCH_W      -1:0] o_rch;


    ///////////////////////////////////////////////////////////////////////////
    // Slave interface 0
    ///////////////////////////////////////////////////////////////////////////

    axicb_slv_if
    #(
    .AXI_ADDR_W        (AXI_ADDR_W),
    .AXI_ID_W          (AXI_ID_W),
    .AXI_DATA_W        (AXI_DATA_W),
    .SLV_NB            (SLV_NB),
    .AXI_SIGNALING     (AXI_SIGNALING),
    .MST_CDC           (MST0_CDC),
    .MST_OSTDREQ_NUM   (MST0_OSTDREQ_NUM),
    .MST_OSTDREQ_SIZE  (MST0_OSTDREQ_SIZE),
    .USER_SUPPORT      (USER_SUPPORT),
    .AXI_AUSER_W       (AXI_AUSER_W),
    .AXI_WUSER_W       (AXI_WUSER_W),
    .AXI_BUSER_W       (AXI_BUSER_W),
    .AXI_RUSER_W       (AXI_RUSER_W),
    .AWCH_W            (AWCH_W),
    .WCH_W             (WCH_W),
    .BCH_W             (BCH_W),
    .ARCH_W            (ARCH_W),
    .RCH_W             (RCH_W)
    )
    slv0_if
    (
    .i_aclk       (slv_clk_0_aclk),
    .i_aresetn    (slv_clk_0_aresetn),
    .i_srst       (slv_clk_0_srst),
    .i_awvalid    (slv_0_aw_valid),
    .i_awready    (slv_0_aw_ready),
    .i_awaddr     (slv_0_aw_addr),
    .i_awlen      (slv_0_aw_len),
    .i_awsize     (slv_0_aw_size),
    .i_awburst    (slv_0_aw_burst),
    .i_awlock     (slv_0_aw_lock),
    .i_awcache    (slv_0_aw_cache),
    .i_awprot     (slv_0_aw_prot),
    .i_awqos      (slv_0_aw_qos),
    .i_awregion   (slv_0_aw_region),
    .i_awid       (slv_0_aw_id),
    .i_awuser     (slv_0_aw_user),
    .i_wvalid     (slv_0_w_valid),
    .i_wready     (slv_0_w_ready),
    .i_wlast      (slv_0_w_last ),
    .i_wdata      (slv_0_w_data),
    .i_wstrb      (slv_0_w_strb),
    .i_wuser      (slv_0_w_user),
    .i_bvalid     (slv_0_b_valid),
    .i_bready     (slv_0_b_ready),
    .i_bid        (slv_0_b_id),
    .i_bresp      (slv_0_b_resp),
    .i_buser      (slv_0_b_user),
    .i_arvalid    (slv_0_ar_valid),
    .i_arready    (slv_0_ar_ready),
    .i_araddr     (slv_0_ar_addr),
    .i_arlen      (slv_0_ar_len),
    .i_arsize     (slv_0_ar_size),
    .i_arburst    (slv_0_ar_burst),
    .i_arlock     (slv_0_ar_lock),
    .i_arcache    (slv_0_ar_cache),
    .i_arprot     (slv_0_ar_prot),
    .i_arqos      (slv_0_ar_qos),
    .i_arregion   (slv_0_ar_region),
    .i_arid       (slv_0_ar_id),
    .i_aruser     (slv_0_ar_user),
    .i_rvalid     (slv_0_r_valid),
    .i_rready     (slv_0_r_ready),
    .i_rid        (slv_0_r_id),
    .i_rresp      (slv_0_r_resp),
    .i_rdata      (slv_0_r_data),
    .i_rlast      (slv_0_r_last),
    .i_ruser      (slv_0_r_user),
    .o_aclk       (aclk),
    .o_aresetn    (aresetn),
    .o_srst       (srst),
    .o_awvalid    (i_awvalid[0]),
    .o_awready    (i_awready[0]),
    .o_awch       (i_awch[0*AWCH_W+:AWCH_W]),
    .o_wvalid     (i_wvalid[0]),
    .o_wready     (i_wready[0]),
    .o_wlast      (i_wlast[0]),
    .o_wch        (i_wch[0*WCH_W+:WCH_W]),
    .o_bvalid     (i_bvalid[0]),
    .o_bready     (i_bready[0]),
    .o_bch        (i_bch[0*BCH_W+:BCH_W]),
    .o_arvalid    (i_arvalid[0]),
    .o_arready    (i_arready[0]),
    .o_arch       (i_arch[0*ARCH_W+:ARCH_W]),
    .o_rvalid     (i_rvalid[0]),
    .o_rready     (i_rready[0]),
    .o_rlast      (i_rlast[0]),
    .o_rch        (i_rch[0*RCH_W+:RCH_W])
    );

    ///////////////////////////////////////////////////////////////////////////
    // Slave interface 1
    ///////////////////////////////////////////////////////////////////////////

    axicb_slv_if
    #(
    .AXI_ADDR_W        (AXI_ADDR_W),
    .AXI_ID_W          (AXI_ID_W),
    .AXI_DATA_W        (AXI_DATA_W),
    .SLV_NB            (SLV_NB),
    .AXI_SIGNALING     (AXI_SIGNALING),
    .MST_CDC           (MST1_CDC),
    .MST_OSTDREQ_NUM   (MST1_OSTDREQ_NUM),
    .MST_OSTDREQ_SIZE  (MST1_OSTDREQ_SIZE),
    .USER_SUPPORT      (USER_SUPPORT),
    .AXI_AUSER_W       (AXI_AUSER_W),
    .AXI_WUSER_W       (AXI_WUSER_W),
    .AXI_BUSER_W       (AXI_BUSER_W),
    .AXI_RUSER_W       (AXI_RUSER_W),
    .AWCH_W            (AWCH_W),
    .WCH_W             (WCH_W),
    .BCH_W             (BCH_W),
    .ARCH_W            (ARCH_W),
    .RCH_W             (RCH_W)
    )
    slv1_if
    (
    .i_aclk       (slv_clk_1_aclk),
    .i_aresetn    (slv_clk_1_aresetn),
    .i_srst       (slv_clk_1_srst),
    .i_awvalid    (slv_1_aw_valid),
    .i_awready    (slv_1_aw_ready),
    .i_awaddr     (slv_1_aw_addr),
    .i_awlen      (slv_1_aw_len),
    .i_awsize     (slv_1_aw_size),
    .i_awburst    (slv_1_aw_burst),
    .i_awlock     (slv_1_aw_lock),
    .i_awcache    (slv_1_aw_cache),
    .i_awprot     (slv_1_aw_prot),
    .i_awqos      (slv_1_aw_qos),
    .i_awregion   (slv_1_aw_region),
    .i_awid       (slv_1_aw_id),
    .i_awuser     (slv_1_aw_user),
    .i_wvalid     (slv_1_w_valid),
    .i_wready     (slv_1_w_ready),
    .i_wlast      (slv_1_w_last ),
    .i_wdata      (slv_1_w_data),
    .i_wstrb      (slv_1_w_strb),
    .i_wuser      (slv_1_w_user),
    .i_bvalid     (slv_1_b_valid),
    .i_bready     (slv_1_b_ready),
    .i_bid        (slv_1_b_id),
    .i_bresp      (slv_1_b_resp),
    .i_buser      (slv_1_b_user),
    .i_arvalid    (slv_1_ar_valid),
    .i_arready    (slv_1_ar_ready),
    .i_araddr     (slv_1_ar_addr),
    .i_arlen      (slv_1_ar_len),
    .i_arsize     (slv_1_ar_size),
    .i_arburst    (slv_1_ar_burst),
    .i_arlock     (slv_1_ar_lock),
    .i_arcache    (slv_1_ar_cache),
    .i_arprot     (slv_1_ar_prot),
    .i_arqos      (slv_1_ar_qos),
    .i_arregion   (slv_1_ar_region),
    .i_arid       (slv_1_ar_id),
    .i_aruser     (slv_1_ar_user),
    .i_rvalid     (slv_1_r_valid),
    .i_rready     (slv_1_r_ready),
    .i_rid        (slv_1_r_id),
    .i_rresp      (slv_1_r_resp),
    .i_rdata      (slv_1_r_data),
    .i_rlast      (slv_1_r_last),
    .i_ruser      (slv_1_r_user),
    .o_aclk       (aclk),
    .o_aresetn    (aresetn),
    .o_srst       (srst),
    .o_awvalid    (i_awvalid[1]),
    .o_awready    (i_awready[1]),
    .o_awch       (i_awch[1*AWCH_W+:AWCH_W]),
    .o_wvalid     (i_wvalid[1]),
    .o_wready     (i_wready[1]),
    .o_wlast      (i_wlast[1]),
    .o_wch        (i_wch[1*WCH_W+:WCH_W]),
    .o_bvalid     (i_bvalid[1]),
    .o_bready     (i_bready[1]),
    .o_bch        (i_bch[1*BCH_W+:BCH_W]),
    .o_arvalid    (i_arvalid[1]),
    .o_arready    (i_arready[1]),
    .o_arch       (i_arch[1*ARCH_W+:ARCH_W]),
    .o_rvalid     (i_rvalid[1]),
    .o_rready     (i_rready[1]),
    .o_rlast      (i_rlast[1]),
    .o_rch        (i_rch[1*RCH_W+:RCH_W])
    );

    ///////////////////////////////////////////////////////////////////////////
    // Slave interface 2
    ///////////////////////////////////////////////////////////////////////////

    axicb_slv_if
    #(
    .AXI_ADDR_W        (AXI_ADDR_W),
    .AXI_ID_W          (AXI_ID_W),
    .AXI_DATA_W        (AXI_DATA_W),
    .SLV_NB            (SLV_NB),
    .AXI_SIGNALING     (AXI_SIGNALING),
    .MST_CDC           (MST2_CDC),
    .MST_OSTDREQ_NUM   (MST2_OSTDREQ_NUM),
    .MST_OSTDREQ_SIZE  (MST2_OSTDREQ_SIZE),
    .USER_SUPPORT      (USER_SUPPORT),
    .AXI_AUSER_W       (AXI_AUSER_W),
    .AXI_WUSER_W       (AXI_WUSER_W),
    .AXI_BUSER_W       (AXI_BUSER_W),
    .AXI_RUSER_W       (AXI_RUSER_W),
    .AWCH_W            (AWCH_W),
    .WCH_W             (WCH_W),
    .BCH_W             (BCH_W),
    .ARCH_W            (ARCH_W),
    .RCH_W             (RCH_W)
    )
    slv2_if
    (
    .i_aclk       (slv_clk_2_aclk),
    .i_aresetn    (slv_clk_2_aresetn),
    .i_srst       (slv_clk_2_srst),
    .i_awvalid    (slv_2_aw_valid),
    .i_awready    (slv_2_aw_ready),
    .i_awaddr     (slv_2_aw_addr),
    .i_awlen      (slv_2_aw_len),
    .i_awsize     (slv_2_aw_size),
    .i_awburst    (slv_2_aw_burst),
    .i_awlock     (slv_2_aw_lock),
    .i_awcache    (slv_2_aw_cache),
    .i_awprot     (slv_2_aw_prot),
    .i_awqos      (slv_2_aw_qos),
    .i_awregion   (slv_2_aw_region),
    .i_awid       (slv_2_aw_id),
    .i_awuser     (slv_2_aw_user),
    .i_wvalid     (slv_2_w_valid),
    .i_wready     (slv_2_w_ready),
    .i_wlast      (slv_2_w_last ),
    .i_wdata      (slv_2_w_data),
    .i_wstrb      (slv_2_w_strb),
    .i_wuser      (slv_2_w_user),
    .i_bvalid     (slv_2_b_valid),
    .i_bready     (slv_2_b_ready),
    .i_bid        (slv_2_b_id),
    .i_bresp      (slv_2_b_resp),
    .i_buser      (slv_2_b_user),
    .i_arvalid    (slv_2_ar_valid),
    .i_arready    (slv_2_ar_ready),
    .i_araddr     (slv_2_ar_addr),
    .i_arlen      (slv_2_ar_len),
    .i_arsize     (slv_2_ar_size),
    .i_arburst    (slv_2_ar_burst),
    .i_arlock     (slv_2_ar_lock),
    .i_arcache    (slv_2_ar_cache),
    .i_arprot     (slv_2_ar_prot),
    .i_arqos      (slv_2_ar_qos),
    .i_arregion   (slv_2_ar_region),
    .i_arid       (slv_2_ar_id),
    .i_aruser     (slv_3_ar_user),
    .i_rvalid     (slv_2_r_valid),
    .i_rready     (slv_2_r_ready),
    .i_rid        (slv_2_r_id),
    .i_rresp      (slv_2_r_resp),
    .i_rdata      (slv_2_r_data),
    .i_rlast      (slv_2_r_last),
    .i_ruser      (slv_2_r_user),
    .o_aclk       (aclk),
    .o_aresetn    (aresetn),
    .o_srst       (srst),
    .o_awvalid    (i_awvalid[2]),
    .o_awready    (i_awready[2]),
    .o_awch       (i_awch[2*AWCH_W+:AWCH_W]),
    .o_wvalid     (i_wvalid[2]),
    .o_wready     (i_wready[2]),
    .o_wlast      (i_wlast[2]),
    .o_wch        (i_wch[2*WCH_W+:WCH_W]),
    .o_bvalid     (i_bvalid[2]),
    .o_bready     (i_bready[2]),
    .o_bch        (i_bch[2*BCH_W+:BCH_W]),
    .o_arvalid    (i_arvalid[2]),
    .o_arready    (i_arready[2]),
    .o_arch       (i_arch[2*ARCH_W+:ARCH_W]),
    .o_rvalid     (i_rvalid[2]),
    .o_rready     (i_rready[2]),
    .o_rlast      (i_rlast[2]),
    .o_rch        (i_rch[2*RCH_W+:RCH_W])
    );

    ///////////////////////////////////////////////////////////////////////////
    // Slave interface 3
    ///////////////////////////////////////////////////////////////////////////

    axicb_slv_if
    #(
    .AXI_ADDR_W        (AXI_ADDR_W),
    .AXI_ID_W          (AXI_ID_W),
    .AXI_DATA_W        (AXI_DATA_W),
    .SLV_NB            (SLV_NB),
    .AXI_SIGNALING     (AXI_SIGNALING),
    .MST_CDC           (MST3_CDC),
    .MST_OSTDREQ_NUM   (MST3_OSTDREQ_NUM),
    .MST_OSTDREQ_SIZE  (MST3_OSTDREQ_SIZE),
    .USER_SUPPORT      (USER_SUPPORT),
    .AXI_AUSER_W       (AXI_AUSER_W),
    .AXI_WUSER_W       (AXI_WUSER_W),
    .AXI_BUSER_W       (AXI_BUSER_W),
    .AXI_RUSER_W       (AXI_RUSER_W),
    .AWCH_W            (AWCH_W),
    .WCH_W             (WCH_W),
    .BCH_W             (BCH_W),
    .ARCH_W            (ARCH_W),
    .RCH_W             (RCH_W)
    )
    slv3_if
    (
    .i_aclk       (slv_clk_3_aclk),
    .i_aresetn    (slv_clk_3_aresetn),
    .i_srst       (slv_clk_3_srst),
    .i_awvalid    (slv_3_aw_valid),
    .i_awready    (slv_3_aw_ready),
    .i_awaddr     (slv_3_aw_addr),
    .i_awlen      (slv_3_aw_len),
    .i_awsize     (slv_3_aw_size),
    .i_awburst    (slv_3_aw_burst),
    .i_awlock     (slv_3_aw_lock),
    .i_awcache    (slv_3_aw_cache),
    .i_awprot     (slv_3_aw_prot),
    .i_awqos      (slv_3_aw_qos),
    .i_awregion   (slv_3_aw_region),
    .i_awid       (slv_3_aw_id),
    .i_awuser     (slv_3_aw_user),
    .i_wvalid     (slv_3_w_valid),
    .i_wready     (slv_3_w_ready),
    .i_wlast      (slv_3_w_last ),
    .i_wdata      (slv_3_w_data),
    .i_wstrb      (slv_3_w_strb),
    .i_wuser      (slv_3_w_user),
    .i_bvalid     (slv_3_b_valid),
    .i_bready     (slv_3_b_ready),
    .i_bid        (slv_3_b_id),
    .i_bresp      (slv_3_b_resp),
    .i_buser      (slv_3_b_user),
    .i_arvalid    (slv_3_ar_valid),
    .i_arready    (slv_3_ar_ready),
    .i_araddr     (slv_3_ar_addr),
    .i_arlen      (slv_3_ar_len),
    .i_arsize     (slv_3_ar_size),
    .i_arburst    (slv_3_ar_burst),
    .i_arlock     (slv_3_ar_lock),
    .i_arcache    (slv_3_ar_cache),
    .i_arprot     (slv_3_ar_prot),
    .i_arqos      (slv_3_ar_qos),
    .i_arregion   (slv_3_ar_region),
    .i_arid       (slv_3_ar_id),
    .i_aruser     (slv_3_ar_user),
    .i_rvalid     (slv_3_r_valid),
    .i_rready     (slv_3_r_ready),
    .i_rid        (slv_3_r_id),
    .i_rresp      (slv_3_r_resp),
    .i_rdata      (slv_3_r_data),
    .i_rlast      (slv_3_r_last),
    .i_ruser      (slv_3_r_user),
    .o_aclk       (aclk),
    .o_aresetn    (aresetn),
    .o_srst       (srst),
    .o_awvalid    (i_awvalid[3]),
    .o_awready    (i_awready[3]),
    .o_awch       (i_awch[3*AWCH_W+:AWCH_W]),
    .o_wvalid     (i_wvalid[3]),
    .o_wready     (i_wready[3]),
    .o_wlast      (i_wlast[3]),
    .o_wch        (i_wch[3*WCH_W+:WCH_W]),
    .o_bvalid     (i_bvalid[3]),
    .o_bready     (i_bready[3]),
    .o_bch        (i_bch[3*BCH_W+:BCH_W]),
    .o_arvalid    (i_arvalid[3]),
    .o_arready    (i_arready[3]),
    .o_arch       (i_arch[3*ARCH_W+:ARCH_W]),
    .o_rvalid     (i_rvalid[3]),
    .o_rready     (i_rready[3]),
    .o_rlast      (i_rlast[3]),
    .o_rch        (i_rch[3*RCH_W+:RCH_W])
    );

    ///////////////////////////////////////////////////////////////////////////
    // AXI switching logic
    ///////////////////////////////////////////////////////////////////////////

    axicb_switch_top
    #(
    .AXI_ADDR_W         (AXI_ADDR_W),
    .AXI_ID_W           (AXI_ID_W),
    .AXI_DATA_W         (AXI_DATA_W),
    .AXI_SIGNALING      (AXI_SIGNALING),
    .MST_NB             (MST_NB),
    .SLV_NB             (SLV_NB),
    .MST_PIPELINE       (MST_PIPELINE),
    .SLV_PIPELINE       (SLV_PIPELINE),
    .TIMEOUT_ENABLE     (TIMEOUT_ENABLE),
    .MST0_ID_MASK       (MST0_ID_MASK),
    .MST1_ID_MASK       (MST1_ID_MASK),
    .MST2_ID_MASK       (MST2_ID_MASK),
    .MST3_ID_MASK       (MST3_ID_MASK),
    .MST_ROUTES         (MST_ROUTES),
    .MST0_PRIORITY      (MST0_PRIORITY),
    .MST1_PRIORITY      (MST1_PRIORITY),
    .MST2_PRIORITY      (MST2_PRIORITY),
    .MST3_PRIORITY      (MST3_PRIORITY),
    .SLV0_START_ADDR    (SLV0_START_ADDR),
    .SLV0_END_ADDR      (SLV0_END_ADDR),
    .SLV1_START_ADDR    (SLV1_START_ADDR),
    .SLV1_END_ADDR      (SLV1_END_ADDR),
    .SLV2_START_ADDR    (SLV2_START_ADDR),
    .SLV2_END_ADDR      (SLV2_END_ADDR),
    .SLV3_START_ADDR    (SLV3_START_ADDR),
    .SLV3_END_ADDR      (SLV3_END_ADDR),
    .AWCH_W             (AWCH_W),
    .WCH_W              (WCH_W),
    .BCH_W              (BCH_W),
    .ARCH_W             (ARCH_W),
    .RCH_W              (RCH_W)
    )
    switchs
    (
    .aclk      (aclk),
    .aresetn   (aresetn),
    .srst      (srst),
    .i_awvalid (i_awvalid),
    .i_awready (i_awready),
    .i_awch    (i_awch),
    .i_wvalid  (i_wvalid),
    .i_wready  (i_wready),
    .i_wlast   (i_wlast),
    .i_wch     (i_wch),
    .i_bvalid  (i_bvalid),
    .i_bready  (i_bready),
    .i_bch     (i_bch),
    .i_arvalid (i_arvalid),
    .i_arready (i_arready),
    .i_arch    (i_arch),
    .i_rvalid  (i_rvalid),
    .i_rready  (i_rready),
    .i_rlast   (i_rlast),
    .i_rch     (i_rch),
    .o_awvalid (o_awvalid),
    .o_awready (o_awready),
    .o_awch    (o_awch),
    .o_wvalid  (o_wvalid),
    .o_wready  (o_wready),
    .o_wlast   (o_wlast),
    .o_wch     (o_wch),
    .o_bvalid  (o_bvalid),
    .o_bready  (o_bready),
    .o_bch     (o_bch),
    .o_arvalid (o_arvalid),
    .o_arready (o_arready),
    .o_arch    (o_arch),
    .o_rvalid  (o_rvalid),
    .o_rready  (o_rready),
    .o_rlast   (o_rlast),
    .o_rch     (o_rch)
    );


    ///////////////////////////////////////////////////////////////////////////
    // Master 0 interface
    ///////////////////////////////////////////////////////////////////////////

    axicb_mst_if
    #(
    .AXI_ADDR_W       (AXI_ADDR_W),
    .AXI_ID_W         (AXI_ID_W),
    .AXI_DATA_W       (AXI_DATA_W),
    .AXI_SIGNALING    (AXI_SIGNALING),
    .SLV_CDC          (SLV0_CDC),
    .SLV_OSTDREQ_NUM  (SLV0_OSTDREQ_NUM),
    .SLV_OSTDREQ_SIZE (SLV0_OSTDREQ_SIZE),
    .USER_SUPPORT     (USER_SUPPORT),
    .KEEP_BASE_ADDR   (SLV0_KEEP_BASE_ADDR),
    .BASE_ADDR        (SLV0_START_ADDR),
    .AXI_AUSER_W      (AXI_AUSER_W),
    .AXI_WUSER_W      (AXI_WUSER_W),
    .AXI_BUSER_W      (AXI_BUSER_W),
    .AXI_RUSER_W      (AXI_RUSER_W),
    .AWCH_W           (AWCH_W),
    .WCH_W            (WCH_W),
    .BCH_W            (BCH_W),
    .ARCH_W           (ARCH_W),
    .RCH_W            (RCH_W)
    )
    mst0_if
    (
    .i_aclk       (aclk),
    .i_aresetn    (aresetn),
    .i_srst       (srst),
    .i_awvalid    (o_awvalid[0]),
    .i_awready    (o_awready[0]),
    .i_awch       (o_awch[0*AWCH_W+:AWCH_W]),
    .i_wvalid     (o_wvalid[0]),
    .i_wready     (o_wready[0]),
    .i_wlast      (o_wlast[0]),
    .i_wch        (o_wch[0*WCH_W+:WCH_W]),
    .i_bvalid     (o_bvalid[0]),
    .i_bready     (o_bready[0]),
    .i_bch        (o_bch[0*BCH_W+:BCH_W]),
    .i_arvalid    (o_arvalid[0]),
    .i_arready    (o_arready[0]),
    .i_arch       (o_arch[0*ARCH_W+:ARCH_W]),
    .i_rvalid     (o_rvalid[0]),
    .i_rready     (o_rready[0]),
    .i_rlast      (o_rlast[0]),
    .i_rch        (o_rch[0*RCH_W+:RCH_W]),
    .o_aclk       (mst_clk_0_aclk),
    .o_aresetn    (mst_clk_0_aresetn),
    .o_srst       (mst_clk_0_srst),
    .o_awvalid    (mst_0_aw_valid),
    .o_awready    (mst_0_aw_ready),
    .o_awaddr     (mst_0_aw_addr),
    .o_awlen      (mst_0_aw_len),
    .o_awsize     (mst_0_aw_size),
    .o_awburst    (mst_0_aw_burst),
    .o_awlock     (mst_0_aw_lock),
    .o_awcache    (mst_0_aw_cache),
    .o_awprot     (mst_0_aw_prot),
    .o_awqos      (mst_0_aw_qos),
    .o_awregion   (mst_0_aw_region),
    .o_awid       (mst_0_aw_id),
    .o_awuser     (mst_0_aw_user),
    .o_wvalid     (mst_0_w_valid),
    .o_wready     (mst_0_w_ready),
    .o_wlast      (mst_0_w_last),
    .o_wdata      (mst_0_w_data),
    .o_wstrb      (mst_0_w_strb),
    .o_wuser      (mst_0_w_user),
    .o_bvalid     (mst_0_b_valid),
    .o_bready     (mst_0_b_ready),
    .o_bid        (mst_0_b_id),
    .o_bresp      (mst_0_b_resp),
    .o_buser      (mst_0_b_user),
    .o_arvalid    (mst_0_ar_valid),
    .o_arready    (mst_0_ar_ready),
    .o_araddr     (mst_0_ar_addr),
    .o_arlen      (mst_0_ar_len),
    .o_arsize     (mst_0_ar_size),
    .o_arburst    (mst_0_ar_burst),
    .o_arlock     (mst_0_ar_lock),
    .o_arcache    (mst_0_ar_cache),
    .o_arprot     (mst_0_ar_prot),
    .o_arqos      (mst_0_ar_qos),
    .o_arregion   (mst_0_ar_region),
    .o_arid       (mst_0_ar_id),
    .o_aruser     (mst_0_ar_user),
    .o_rvalid     (mst_0_r_valid),
    .o_rready     (mst_0_r_ready),
    .o_rid        (mst_0_r_id),
    .o_rresp      (mst_0_r_resp),
    .o_rdata      (mst_0_r_data),
    .o_rlast      (mst_0_r_last),
    .o_ruser      (mst_0_r_user)
    );

    ///////////////////////////////////////////////////////////////////////////
    // Master 1 interface
    ///////////////////////////////////////////////////////////////////////////

    axicb_mst_if
    #(
    .AXI_ADDR_W       (AXI_ADDR_W),
    .AXI_ID_W         (AXI_ID_W),
    .AXI_DATA_W       (AXI_DATA_W),
    .AXI_SIGNALING    (AXI_SIGNALING),
    .SLV_CDC          (SLV1_CDC),
    .SLV_OSTDREQ_NUM  (SLV1_OSTDREQ_NUM),
    .SLV_OSTDREQ_SIZE (SLV1_OSTDREQ_SIZE),
    .USER_SUPPORT     (USER_SUPPORT),
    .KEEP_BASE_ADDR   (SLV1_KEEP_BASE_ADDR),
    .BASE_ADDR        (SLV1_START_ADDR),
    .AXI_AUSER_W      (AXI_AUSER_W),
    .AXI_WUSER_W      (AXI_WUSER_W),
    .AXI_BUSER_W      (AXI_BUSER_W),
    .AXI_RUSER_W      (AXI_RUSER_W),
    .AWCH_W           (AWCH_W),
    .WCH_W            (WCH_W),
    .BCH_W            (BCH_W),
    .ARCH_W           (ARCH_W),
    .RCH_W            (RCH_W)
    )
    mst1_if
    (
    .i_aclk       (aclk),
    .i_aresetn    (aresetn),
    .i_srst       (srst),
    .i_awvalid    (o_awvalid[1]),
    .i_awready    (o_awready[1]),
    .i_awch       (o_awch[1*AWCH_W+:AWCH_W]),
    .i_wvalid     (o_wvalid[1]),
    .i_wready     (o_wready[1]),
    .i_wlast      (o_wlast[1]),
    .i_wch        (o_wch[1*WCH_W+:WCH_W]),
    .i_bvalid     (o_bvalid[1]),
    .i_bready     (o_bready[1]),
    .i_bch        (o_bch[1*BCH_W+:BCH_W]),
    .i_arvalid    (o_arvalid[1]),
    .i_arready    (o_arready[1]),
    .i_arch       (o_arch[1*ARCH_W+:ARCH_W]),
    .i_rvalid     (o_rvalid[1]),
    .i_rready     (o_rready[1]),
    .i_rlast      (o_rlast[1]),
    .i_rch        (o_rch[1*RCH_W+:RCH_W]),
    .o_aclk       (mst_clk_1_aclk),
    .o_aresetn    (mst_clk_1_aresetn),
    .o_srst       (mst_clk_1_srst),
    .o_awvalid    (mst_1_aw_valid),
    .o_awready    (mst_1_aw_ready),
    .o_awaddr     (mst_1_aw_addr),
    .o_awlen      (mst_1_aw_len),
    .o_awsize     (mst_1_aw_size),
    .o_awburst    (mst_1_aw_burst),
    .o_awlock     (mst_1_aw_lock),
    .o_awcache    (mst_1_aw_cache),
    .o_awprot     (mst_1_aw_prot),
    .o_awqos      (mst_1_aw_qos),
    .o_awregion   (mst_1_aw_region),
    .o_awid       (mst_1_aw_id),
    .o_awuser     (mst_1_aw_user),
    .o_wvalid     (mst_1_w_valid),
    .o_wready     (mst_1_w_ready),
    .o_wlast      (mst_1_w_last),
    .o_wdata      (mst_1_w_data),
    .o_wstrb      (mst_1_w_strb),
    .o_wuser      (mst_1_w_user),
    .o_bvalid     (mst_1_b_valid),
    .o_bready     (mst_1_b_ready),
    .o_bid        (mst_1_b_id),
    .o_bresp      (mst_1_b_resp),
    .o_buser      (mst_1_b_user),
    .o_arvalid    (mst_1_ar_valid),
    .o_arready    (mst_1_ar_ready),
    .o_araddr     (mst_1_ar_addr),
    .o_arlen      (mst_1_ar_len),
    .o_arsize     (mst_1_ar_size),
    .o_arburst    (mst_1_ar_burst),
    .o_arlock     (mst_1_ar_lock),
    .o_arcache    (mst_1_ar_cache),
    .o_arprot     (mst_1_ar_prot),
    .o_arqos      (mst_1_ar_qos),
    .o_arregion   (mst_1_ar_region),
    .o_arid       (mst_1_ar_id),
    .o_aruser     (mst_1_ar_user),
    .o_rvalid     (mst_1_r_valid),
    .o_rready     (mst_1_r_ready),
    .o_rid        (mst_1_r_id),
    .o_rresp      (mst_1_r_resp),
    .o_rdata      (mst_1_r_data),
    .o_rlast      (mst_1_r_last),
    .o_ruser      (mst_1_r_user)
    );

    ///////////////////////////////////////////////////////////////////////////
    // Master 2 Interface
    ///////////////////////////////////////////////////////////////////////////

    axicb_mst_if
    #(
    .AXI_ADDR_W       (AXI_ADDR_W),
    .AXI_ID_W         (AXI_ID_W),
    .AXI_DATA_W       (AXI_DATA_W),
    .AXI_SIGNALING    (AXI_SIGNALING),
    .SLV_CDC          (SLV2_CDC),
    .SLV_OSTDREQ_NUM  (SLV2_OSTDREQ_NUM),
    .SLV_OSTDREQ_SIZE (SLV2_OSTDREQ_SIZE),
    .USER_SUPPORT     (USER_SUPPORT),
    .KEEP_BASE_ADDR   (SLV2_KEEP_BASE_ADDR),
    .BASE_ADDR        (SLV2_START_ADDR),
    .AXI_AUSER_W      (AXI_AUSER_W),
    .AXI_WUSER_W      (AXI_WUSER_W),
    .AXI_BUSER_W      (AXI_BUSER_W),
    .AXI_RUSER_W      (AXI_RUSER_W),
    .AWCH_W           (AWCH_W),
    .WCH_W            (WCH_W),
    .BCH_W            (BCH_W),
    .ARCH_W           (ARCH_W),
    .RCH_W            (RCH_W)
    )
    mst2_if
    (
    .i_aclk       (aclk),
    .i_aresetn    (aresetn),
    .i_srst       (srst),
    .i_awvalid    (o_awvalid[2]),
    .i_awready    (o_awready[2]),
    .i_awch       (o_awch[2*AWCH_W+:AWCH_W]),
    .i_wvalid     (o_wvalid[2]),
    .i_wready     (o_wready[2]),
    .i_wlast      (o_wlast[2]),
    .i_wch        (o_wch[2*WCH_W+:WCH_W]),
    .i_bvalid     (o_bvalid[2]),
    .i_bready     (o_bready[2]),
    .i_bch        (o_bch[2*BCH_W+:BCH_W]),
    .i_arvalid    (o_arvalid[2]),
    .i_arready    (o_arready[2]),
    .i_arch       (o_arch[2*ARCH_W+:ARCH_W]),
    .i_rvalid     (o_rvalid[2]),
    .i_rready     (o_rready[2]),
    .i_rlast      (o_rlast[2]),
    .i_rch        (o_rch[2*RCH_W+:RCH_W]),
    .o_aclk       (mst_clk_2_aclk),
    .o_aresetn    (mst_clk_2_aresetn),
    .o_srst       (mst_clk_2_srst),
    .o_awvalid    (mst_2_aw_valid),
    .o_awready    (mst_2_aw_ready),
    .o_awaddr     (mst_2_aw_addr),
    .o_awlen      (mst_2_aw_len),
    .o_awsize     (mst_2_aw_size),
    .o_awburst    (mst_2_aw_burst),
    .o_awlock     (mst_2_aw_lock),
    .o_awcache    (mst_2_aw_cache),
    .o_awprot     (mst_2_aw_prot),
    .o_awqos      (mst_2_aw_qos),
    .o_awregion   (mst_2_aw_region),
    .o_awid       (mst_2_aw_id),
    .o_awuser     (mst_2_aw_user),
    .o_wvalid     (mst_2_w_valid),
    .o_wready     (mst_2_w_ready),
    .o_wlast      (mst_2_w_last),
    .o_wdata      (mst_2_w_data),
    .o_wstrb      (mst_2_w_strb),
    .o_wuser      (mst_2_w_user),
    .o_bvalid     (mst_2_b_valid),
    .o_bready     (mst_2_b_ready),
    .o_bid        (mst_2_b_id),
    .o_bresp      (mst_2_b_resp),
    .o_buser      (mst_2_b_user),
    .o_arvalid    (mst_2_ar_valid),
    .o_arready    (mst_2_ar_ready),
    .o_araddr     (mst_2_ar_addr),
    .o_arlen      (mst_2_ar_len),
    .o_arsize     (mst_2_ar_size),
    .o_arburst    (mst_2_ar_burst),
    .o_arlock     (mst_2_ar_lock),
    .o_arcache    (mst_2_ar_cache),
    .o_arprot     (mst_2_ar_prot),
    .o_arqos      (mst_2_ar_qos),
    .o_arregion   (mst_2_ar_region),
    .o_arid       (mst_2_ar_id),
    .o_aruser     (mst_2_ar_user),
    .o_rvalid     (mst_2_r_valid),
    .o_rready     (mst_2_r_ready),
    .o_rid        (mst_2_r_id),
    .o_rresp      (mst_2_r_resp),
    .o_rdata      (mst_2_r_data),
    .o_rlast      (mst_2_r_last),
    .o_ruser      (mst_2_r_user)
    );

    ///////////////////////////////////////////////////////////////////////////
    // Master 3 Interface
    ///////////////////////////////////////////////////////////////////////////

    axicb_mst_if
    #(
    .AXI_ADDR_W       (AXI_ADDR_W),
    .AXI_ID_W         (AXI_ID_W),
    .AXI_DATA_W       (AXI_DATA_W),
    .AXI_SIGNALING    (AXI_SIGNALING),
    .SLV_CDC          (SLV3_CDC),
    .SLV_OSTDREQ_NUM  (SLV3_OSTDREQ_NUM),
    .SLV_OSTDREQ_SIZE (SLV3_OSTDREQ_SIZE),
    .KEEP_BASE_ADDR   (SLV3_KEEP_BASE_ADDR),
    .BASE_ADDR        (SLV3_START_ADDR),
    .USER_SUPPORT     (USER_SUPPORT),
    .AXI_AUSER_W      (AXI_AUSER_W),
    .AXI_WUSER_W      (AXI_WUSER_W),
    .AXI_BUSER_W      (AXI_BUSER_W),
    .AXI_RUSER_W      (AXI_RUSER_W),
    .AWCH_W           (AWCH_W),
    .WCH_W            (WCH_W),
    .BCH_W            (BCH_W),
    .ARCH_W           (ARCH_W),
    .RCH_W            (RCH_W)
    )
    mst3_if
    (
    .i_aclk       (aclk),
    .i_aresetn    (aresetn),
    .i_srst       (srst),
    .i_awvalid    (o_awvalid[3]),
    .i_awready    (o_awready[3]),
    .i_awch       (o_awch[3*AWCH_W+:AWCH_W]),
    .i_wvalid     (o_wvalid[3]),
    .i_wready     (o_wready[3]),
    .i_wlast      (o_wlast[3]),
    .i_wch        (o_wch[3*WCH_W+:WCH_W]),
    .i_bvalid     (o_bvalid[3]),
    .i_bready     (o_bready[3]),
    .i_bch        (o_bch[3*BCH_W+:BCH_W]),
    .i_arvalid    (o_arvalid[3]),
    .i_arready    (o_arready[3]),
    .i_arch       (o_arch[3*ARCH_W+:ARCH_W]),
    .i_rvalid     (o_rvalid[3]),
    .i_rready     (o_rready[3]),
    .i_rlast      (o_rlast[3]),
    .i_rch        (o_rch[3*RCH_W+:RCH_W]),
    .o_aclk       (mst_clk_3_aclk),
    .o_aresetn    (mst_clk_3_aresetn),
    .o_srst       (mst_clk_3_srst),
    .o_awvalid    (mst_3_aw_valid),
    .o_awready    (mst_3_aw_ready),
    .o_awaddr     (mst_3_aw_addr),
    .o_awlen      (mst_3_aw_len),
    .o_awsize     (mst_3_aw_size),
    .o_awburst    (mst_3_aw_burst),
    .o_awlock     (mst_3_aw_lock),
    .o_awcache    (mst_3_aw_cache),
    .o_awprot     (mst_3_aw_prot),
    .o_awqos      (mst_3_aw_qos),
    .o_awregion   (mst_3_aw_region),
    .o_awid       (mst_3_aw_id),
    .o_awuser     (mst_3_aw_user),
    .o_wvalid     (mst_3_w_valid),
    .o_wready     (mst_3_w_ready),
    .o_wlast      (mst_3_w_last),
    .o_wdata      (mst_3_w_data),
    .o_wstrb      (mst_3_w_strb),
    .o_wuser      (mst_3_w_user),
    .o_bvalid     (mst_3_b_valid),
    .o_bready     (mst_3_b_ready),
    .o_bid        (mst_3_b_id),
    .o_bresp      (mst_3_b_resp),
    .o_buser      (mst_3_b_user),
    .o_arvalid    (mst_3_ar_valid),
    .o_arready    (mst_3_ar_ready),
    .o_araddr     (mst_3_ar_addr),
    .o_arlen      (mst_3_ar_len),
    .o_arsize     (mst_3_ar_size),
    .o_arburst    (mst_3_ar_burst),
    .o_arlock     (mst_3_ar_lock),
    .o_arcache    (mst_3_ar_cache),
    .o_arprot     (mst_3_ar_prot),
    .o_arqos      (mst_3_ar_qos),
    .o_arregion   (mst_3_ar_region),
    .o_arid       (mst_3_ar_id),
    .o_aruser     (mst_3_ar_user),
    .o_rvalid     (mst_3_r_valid),
    .o_rready     (mst_3_r_ready),
    .o_rid        (mst_3_r_id),
    .o_rresp      (mst_3_r_resp),
    .o_rdata      (mst_3_r_data),
    .o_rlast      (mst_3_r_last),
    .o_ruser      (mst_3_r_user)
    );

endmodule

/* verilator lint_on WIDTHTRUNC */
/* verilator lint_on WIDTHEXPAND */

`resetall
