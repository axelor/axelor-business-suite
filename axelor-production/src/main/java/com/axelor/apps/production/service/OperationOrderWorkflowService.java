/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.production.service;

import com.axelor.app.production.db.IOperationOrder;
import com.axelor.app.production.db.IWorkCenter;
import com.axelor.apps.production.db.Machine;
import com.axelor.apps.production.db.OperationOrder;
import com.axelor.apps.production.db.OperationOrderDuration;
import com.axelor.apps.production.db.ProdHumanResource;
import com.axelor.apps.production.db.ProdProcessLine;
import com.axelor.apps.production.db.WorkCenter;
import com.axelor.apps.production.db.repo.OperationOrderDurationRepository;
import com.axelor.apps.production.db.repo.OperationOrderRepository;
import com.axelor.apps.production.service.app.AppProductionService;
import com.axelor.auth.AuthUtils;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class OperationOrderWorkflowService {
	protected OperationOrderStockMoveService operationOrderStockMoveService;
	protected OperationOrderRepository operationOrderRepo;
	protected OperationOrderDurationRepository operationOrderDurationRepo;

	protected LocalDateTime now;
	
	@Inject
	public OperationOrderWorkflowService(OperationOrderStockMoveService operationOrderStockMoveService, OperationOrderRepository operationOrderRepo,
										 OperationOrderDurationRepository operationOrderDurationRepo, AppProductionService appProductionService) {
		this.operationOrderStockMoveService = operationOrderStockMoveService;
		this.operationOrderRepo = operationOrderRepo;
		this.operationOrderDurationRepo = operationOrderDurationRepo;

		now = appProductionService.getTodayDateTime().toLocalDateTime();
	}

	@Transactional
	public OperationOrder plan(OperationOrder operationOrder) throws AxelorException  {

		operationOrder.setPlannedStartDateT(this.getLastOperationOrder(operationOrder));

		operationOrder.setPlannedEndDateT(this.computePlannedEndDateT(operationOrder));

		operationOrder.setPlannedDuration(
				this.getDuration(
				this.computeDuration(operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT())));

		operationOrderStockMoveService.createToConsumeStockMove(operationOrder);

		operationOrder.setStatusSelect(IOperationOrder.STATUS_PLANNED);

		return operationOrderRepo.save(operationOrder);
	}
	
	@Transactional
	public OperationOrder replan(OperationOrder operationOrder) throws AxelorException  {

		operationOrder.setPlannedStartDateT(this.getLastOperationOrder(operationOrder));

		operationOrder.setPlannedEndDateT(this.computePlannedEndDateT(operationOrder));

		operationOrder.setPlannedDuration(
				this.getDuration(
				this.computeDuration(operationOrder.getPlannedStartDateT(), operationOrder.getPlannedEndDateT())));

		return operationOrderRepo.save(operationOrder);
	}


	public LocalDateTime getLastOperationOrder(OperationOrder operationOrder)  {

		OperationOrder lastOperationOrder = operationOrderRepo.all().filter("self.manufOrder = ?1 AND self.priority <= ?2 AND self.statusSelect >= 3 AND self.statusSelect < 6 AND self.id != ?3",
				operationOrder.getManufOrder(), operationOrder.getPriority(), operationOrder.getId()).order("-self.priority").order("-self.plannedEndDateT").fetchOne();
		
		if(lastOperationOrder != null)  {
			if(lastOperationOrder.getPriority() == operationOrder.getPriority())  {
				if(lastOperationOrder.getPlannedStartDateT() != null && lastOperationOrder.getPlannedStartDateT().isAfter(operationOrder.getManufOrder().getPlannedStartDateT()))  {
					if(lastOperationOrder.getMachineWorkCenter().equals(operationOrder.getMachineWorkCenter())){
						return lastOperationOrder.getPlannedEndDateT();
					}
					return lastOperationOrder.getPlannedStartDateT();
				}
				else  {
					return operationOrder.getManufOrder().getPlannedStartDateT();
				}
			}
			else  {
				if(lastOperationOrder.getPlannedEndDateT() != null && lastOperationOrder.getPlannedEndDateT().isAfter(operationOrder.getManufOrder().getPlannedStartDateT()))  {
					return lastOperationOrder.getPlannedEndDateT();
				}
				else  {
					return operationOrder.getManufOrder().getPlannedStartDateT();
				}
			}
		}

		return operationOrder.getManufOrder().getPlannedStartDateT();
	}


	/**
	 * Starts the given {@link OperationOrder} and sets its starting time
	 *
	 * @param operationOrder An operation order
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void start(OperationOrder operationOrder) {
		if (operationOrder.getStatusSelect() != IOperationOrder.STATUS_IN_PROGRESS) {
			operationOrder.setStatusSelect(IOperationOrder.STATUS_IN_PROGRESS);
			operationOrder.setRealStartDateT(now);

			startOperationOrderDuration(operationOrder);

			operationOrderRepo.save(operationOrder);
		}
	}

	/**
	 * Pauses the given {@link OperationOrder} and sets its pausing time
	 *
	 * @param operationOrder An operation order
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void pause(OperationOrder operationOrder) {
		operationOrder.setStatusSelect(IOperationOrder.STATUS_STANDBY);

		stopOperationOrderDuration(operationOrder);

		operationOrderRepo.save(operationOrder);
	}

	/**
	 * Resumes the given {@link OperationOrder} and sets its resuming time
	 *
	 * @param operationOrder An operation order
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void resume(OperationOrder operationOrder) {
		operationOrder.setStatusSelect(IOperationOrder.STATUS_IN_PROGRESS);

		startOperationOrderDuration(operationOrder);

		operationOrderRepo.save(operationOrder);
	}

	/**
	 * Ends the given {@link OperationOrder} and sets its stopping time<br>
	 * Realizes the linked stock moves
	 *
	 * @param operationOrder An operation order
	 */
	@Transactional
	public void finish(OperationOrder operationOrder) throws AxelorException {
		operationOrder.setStatusSelect(IOperationOrder.STATUS_FINISHED);
		operationOrder.setRealEndDateT(now);

		stopOperationOrderDuration(operationOrder);

		operationOrderStockMoveService.finish(operationOrder);
		operationOrderRepo.save(operationOrder);
	}

	/**
	 * Cancels the given {@link OperationOrder} and its linked stock moves
	 *
	 * @param operationOrder An operation order
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void cancel(OperationOrder operationOrder) throws AxelorException {
		operationOrder.setStatusSelect(IOperationOrder.STATUS_CANCELED);

		operationOrderStockMoveService.cancel(operationOrder);

		operationOrderRepo.save(operationOrder);
	}


	/**
	 * Starts an {@link OperationOrderDuration} and links it to the given {@link OperationOrder}
	 *
	 * @param operationOrder An operation order
	 */
	public void startOperationOrderDuration(OperationOrder operationOrder) {
		OperationOrderDuration duration = new OperationOrderDuration();
		duration.setStartedBy(AuthUtils.getUser());
		duration.setStartingDateTime(now);
		operationOrder.addOperationOrderDurationListItem(duration);
	}

	/**
	 * Ends the last {@link OperationOrderDuration} and sets the real duration of {@code operationOrder}<br>
	 * Adds the real duration to the {@link Machine} linked to {@code operationOrder}
	 *
	 * @param operationOrder An operation order
	 */
	public void stopOperationOrderDuration(OperationOrder operationOrder) {
		OperationOrderDuration duration = operationOrderDurationRepo.all().filter("self.operationOrder.id = ? AND self.stoppedBy IS NULL AND self.stoppingDateTime IS NULL", operationOrder.getId()).fetchOne();
		duration.setStoppedBy(AuthUtils.getUser());
		duration.setStoppingDateTime(now);

		if (operationOrder.getStatusSelect() == IOperationOrder.STATUS_FINISHED) {
			long durationLong = getDuration(computeRealDuration(operationOrder));
			operationOrder.setRealDuration(durationLong);
			Machine machine = operationOrder.getWorkCenter().getMachine();
			machine.setOperatingDuration(machine.getOperatingDuration() + durationLong);
		}

		operationOrderDurationRepo.save(duration);
	}


	/**
	 * Computes the duration of all the {@link OperationOrderDuration} of {@code operationOrder}
	 *
	 * @param operationOrder An operation order
	 * @return Real duration of {@code operationOrder}
	 */
	public Duration computeRealDuration(OperationOrder operationOrder) {
		Duration totalDuration = Duration.ZERO;

		List<OperationOrderDuration> operationOrderDurations = operationOrder.getOperationOrderDurationList();
		for (OperationOrderDuration operationOrderDuration : operationOrderDurations) {
			totalDuration = totalDuration.plus(Duration.between(operationOrderDuration.getStartingDateTime(), operationOrderDuration.getStoppingDateTime()));
		}

		return totalDuration;
	}

	public Duration computeDuration(LocalDateTime startDateTime, LocalDateTime endDateTime)  {

		return Duration.between(startDateTime, endDateTime);

	}

	public long getDuration(Duration duration)  {

		return duration.getSeconds();

	}


	public LocalDateTime computePlannedEndDateT(OperationOrder operationOrder)  {

		if(operationOrder.getWorkCenter() != null)  {
			return operationOrder.getPlannedStartDateT()
					.plusSeconds((int)this.computeEntireCycleDuration(operationOrder, operationOrder.getManufOrder().getQty()));
		}

		return operationOrder.getPlannedStartDateT();
	}


	public long computeEntireCycleDuration(OperationOrder operationOrder, BigDecimal qty)  {

		long machineDuration = this.computeMachineDuration(operationOrder, qty);

		long humanDuration = this.computeHumanDuration(operationOrder, qty);

		if(machineDuration >= humanDuration)  {
			return machineDuration;
		}
		else  {
			return humanDuration;
		}

	}


	public long computeMachineDuration(OperationOrder operationOrder, BigDecimal qty)  {
		ProdProcessLine prodProcessLine = operationOrder.getProdProcessLine();
		WorkCenter workCenter = prodProcessLine.getWorkCenter();
		
		long duration = 0;

		int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

		if(workCenterTypeSelect == IWorkCenter.WORK_CENTER_MACHINE || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH)  {
			Machine machine = workCenter.getMachine();
			duration += machine.getStartingDuration();

			BigDecimal durationPerCycle = new BigDecimal(prodProcessLine.getDurationPerCycle());
			BigDecimal maxCapacityPerCycle = prodProcessLine.getMaxCapacityPerCycle();

			if (maxCapacityPerCycle.compareTo(BigDecimal.ZERO) == 0) {
				duration += qty.multiply(durationPerCycle).longValue();
			} else {
				duration += (qty.divide(maxCapacityPerCycle)).multiply(durationPerCycle).longValue();
			}

			duration += machine.getEndingDuration();

		}

		return duration;
	}


	public long computeHumanDuration(OperationOrder operationOrder, BigDecimal qty)  {
		WorkCenter workCenter = operationOrder.getProdProcessLine().getWorkCenter();

		long duration = 0;

		int workCenterTypeSelect = workCenter.getWorkCenterTypeSelect();

		if(workCenterTypeSelect == IWorkCenter.WORK_CENTER_HUMAN || workCenterTypeSelect == IWorkCenter.WORK_CENTER_BOTH)  {

			if(operationOrder.getProdHumanResourceList() != null)  {

				for(ProdHumanResource prodHumanResource : operationOrder.getProdHumanResourceList())  {

					duration += prodHumanResource.getDuration();

				}

			}

		}

		return qty.multiply(new BigDecimal(duration)).longValue();
	}
}

